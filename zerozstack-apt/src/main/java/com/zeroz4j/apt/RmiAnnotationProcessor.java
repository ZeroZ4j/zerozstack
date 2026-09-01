/*
 * Copyright 2026 Franz Schöning
 * Project: https://www.zeroz4j.com
 * Author: Franz Schöning - Principal Enterprise Architect (https://www.franzschoning.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zeroz4j.apt;

import com.zeroz4j.api.ClientWritable;
import com.zeroz4j.api.DataModel;
import com.zeroz4j.api.LiveSync;
import com.zeroz4j.api.RequiresRole;
import com.zeroz4j.api.RmiService;
import com.zeroz4j.api.Route;
import com.zeroz4j.api.validation.Max;
import com.zeroz4j.api.validation.Min;
import com.zeroz4j.api.validation.NotBlank;
import com.zeroz4j.api.validation.Size;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * Annotation Processor (APT) for zeroz4j compile-time code generation.
 *
 * <p>Scans for classes annotated with {@link DataModel} and interfaces annotated with {@link RmiService}.</p>
 *
 * <p>Generates high-performance binary serializers ({@code <Model>_Serializer}), client RMI stubs ({@code <Service>_Stub}),
 * and SPI registrars ({@code BinaryPackableRegistrar}) registered via {@code META-INF/services/com.zeroz4j.api.BinaryRegistrar}.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>AOT Serializer Generation:</b> Inspects fields, getters, and setters of {@code @DataModel} classes. Generates static {@code write} and {@code read} methods using primitive byte buffers.</li>
 *   <li><b>RMI Stub Generation:</b> Inspects methods of {@code @RmiService} interfaces. Generates strongly typed proxy stubs delegating calls to {@link com.zeroz4j.api.RmiClientExecutor#executeCall}.</li>
 *   <li><b>SPI Registration:</b> Generates {@code com.zeroz4j.generated.BinaryPackableRegistrar} class and corresponding ServiceLoader config file in {@code META-INF/services}.</li>
 * </ul>
 */
@SupportedAnnotationTypes({
    "com.zeroz4j.api.DataModel",
    "com.zeroz4j.api.RmiService",
    "com.zeroz4j.api.Route"
})
public class RmiAnnotationProcessor extends AbstractProcessor {

    private final List<String> binaryModels = new ArrayList<>();
    /** FQCNs of {@code record} models, for record-delegate registrar generation. */
    private final List<String> binaryRecords = new ArrayList<>();
    /** Sealed wire type FQCN to the FQCNs it permits, for permitted-set registrar generation. */
    private final Map<String, List<String>> sealedBases = new LinkedHashMap<>();
    /** Model FQCN to the FQCN of its generated {@code _Serializer}, which flattens nested names. */
    private final Map<String, String> serializerNames = new LinkedHashMap<>();
    /** FQCNs of models with validation annotations, for registrar generation. */
    private final List<String> validatedModels = new ArrayList<>();
    /** FQCNs of @ClientWritable models, for live-supplier registrar generation. */
    private final List<String> clientWritableModels = new ArrayList<>();
    /** FQCNs of enum types reachable from @DataModel fields, for enum-resolver registrar generation. */
    private final Set<String> enumTypes = new LinkedHashSet<>();
    /** One entry per @Route class, for route-table generation. */
    private final List<RouteEntry> routes = new ArrayList<>();

    /** What the processor learned about one {@code @Route} class. */
    private static final class RouteEntry {
        String fqcn;
        String pattern;
        String layoutFqcn;      // null when the route stands alone
        String label;
        int order;
        boolean layout;         // implements RouteLayout rather than RouteView
        List<String> roles = new ArrayList<>();
    }

    /**
     * Specifies the supported Java source version (latest supported).
     *
     * @return latest supported Java source version
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * Processes annotations during javac compilation rounds.
     *
     * @param annotations set of target annotations to process
     * @param roundEnv    round environment context
     * @return true if annotations were claimed and processed
     *
     * <p><b>Under the hood:</b> Queries {@code roundEnv.getElementsAnnotatedWith(DataModel.class)} and invokes {@link #generateSerializer}.
     * Queries {@code roundEnv.getElementsAnnotatedWith(RmiService.class)} and invokes {@link #generateStub}.
     * Generates SPI registrar via {@link #generateRegistrar()}.</p>
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            return false;
        }

        ProcessingEnvironment env = processingEnv;
        Types typeUtils = env.getTypeUtils();

        // Process Portables
        Set<? extends Element> models = roundEnv.getElementsAnnotatedWith(DataModel.class);
        for (Element element : models) {
            ElementKind kind = element.getKind();
            if (kind != ElementKind.CLASS && kind != ElementKind.RECORD
                    && kind != ElementKind.INTERFACE) {
                // Silence here was the defect: a @DataModel that quietly gets no serializer fails
                // at the first call that tries to send it, far from the annotation.
                env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "@DataModel is only supported on a class, a record, or a sealed interface. "
                    + describeKind(kind) + " cannot be given a generated serializer, and skipping "
                    + "it silently would fail at runtime on the first call that sent one.",
                    element);
                continue;
            }

            TypeElement typeElement = (TypeElement) element;
            boolean sealed = typeElement.getModifiers().contains(Modifier.SEALED);

            if (kind == ElementKind.INTERFACE) {
                if (!sealed) {
                    env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "An interface can only be a wire type when it is sealed. The permitted set "
                        + "is what tells the receiver which classes it is allowed to build; without "
                        + "it, anything at all could be named. Write "
                        + "'sealed interface X permits A, B', or annotate the implementations "
                        + "instead and declare the field as one of them.", element);
                    continue;
                }
                collectSealedBase(typeElement, typeUtils);
                continue;
            }

            if (sealed) {
                if (!typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
                    env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "A sealed class used as a wire type must be abstract. A base that is also a "
                        + "value of its own would be neither in nor out of its permitted set.",
                        element);
                    continue;
                }
                collectSealedBase(typeElement, typeUtils);
                continue;
            }

            if (kind == ElementKind.RECORD) {
                String recordFqcn = typeElement.getQualifiedName().toString();
                if (typeElement.getAnnotation(LiveSync.class) != null
                        || typeElement.getAnnotation(ClientWritable.class) != null) {
                    env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@LiveSync and @ClientWritable cannot be used on a record. Live sync edits "
                        + "one object in place through its setters and reports the change; a "
                        + "record has no setters and never changes. Use a class for anything that "
                        + "is edited after it is made.", element);
                    continue;
                }
                binaryRecords.add(recordFqcn);
                checkFieldTypes(typeElement, typeUtils);
                try {
                    generateRecordSerializer(typeElement, typeUtils);
                } catch (IOException e) {
                    env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Failed to generate serializer for " + recordFqcn + ": " + e.getMessage(),
                        element);
                }
                try {
                    if (generateRules(typeElement, typeUtils)) {
                        validatedModels.add(recordFqcn);
                    }
                } catch (IOException e) {
                    env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Failed to generate validation rules for " + recordFqcn + ": "
                        + e.getMessage(), element);
                }
                continue;
            }

            {
                String fqcn = typeElement.getQualifiedName().toString();
                checkInheritance(typeElement, typeUtils);
                checkFieldTypes(typeElement, typeUtils);
                if (typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
                    // An abstract model is never a value of its own — nothing can construct one, so
                    // it gets no serializer and no entry in the registry. It exists to hand its
                    // fields down, and getFields collects those into every concrete model below it.
                    continue;
                }
                binaryModels.add(fqcn);
                try {
                    generateSerializer(typeElement, typeUtils);
                } catch (IOException e) {
                    env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to generate serializer for " + fqcn + ": " + e.getMessage(), element);
                }
                try {
                    if (generateRules(typeElement, typeUtils)) {
                        validatedModels.add(fqcn);
                    }
                } catch (IOException e) {
                    env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to generate validation rules for " + fqcn + ": " + e.getMessage(), element);
                }
                // A _Live subclass is generated for every @LiveSync model, not only the writable
                // ones: its getters make the object a reactive dependency so an inbound sync
                // re-runs the effects that read it. @ClientWritable additionally overrides the
                // setters to report outbound mutations.
                boolean liveSync = typeElement.getAnnotation(LiveSync.class) != null;
                boolean clientWritable = typeElement.getAnnotation(ClientWritable.class) != null;
                if (liveSync || clientWritable) {
                    try {
                        generateLiveSubclass(typeElement, typeUtils, clientWritable);
                        clientWritableModels.add(fqcn);
                    } catch (IOException e) {
                        env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to generate live subclass for " + fqcn + ": " + e.getMessage(), element);
                    }
                }
            }
        }

        // Process RmiServices
        Set<? extends Element> services = roundEnv.getElementsAnnotatedWith(RmiService.class);
        for (Element element : services) {
            if (element.getKind() == ElementKind.INTERFACE) {
                TypeElement typeElement = (TypeElement) element;
                String fqcn = typeElement.getQualifiedName().toString();
                try {
                    generateStub(typeElement, typeUtils);
                } catch (IOException e) {
                    env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to generate RMI stub for " + fqcn + ": " + e.getMessage(), element);
                }
            }
        }

        // Process @Route views and layouts
        for (Element element : roundEnv.getElementsAnnotatedWith(Route.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                // Same rule as @DataModel above, and for the same reason: a route that is quietly
                // absent from the table is a URL that 404s with nothing to explain why.
                env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "@Route is only supported on a class. " + describeKind(element.getKind())
                    + " cannot be registered as a route: the router needs a public no-argument "
                    + "constructor to build it without reflection.", element);
                continue;
            }
            collectRoute((TypeElement) element, typeUtils);
        }
        if (!routes.isEmpty()) {
            try {
                generateRouteRegistrar();
            } catch (IOException e) {
                env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate the route table: " + e.getMessage());
            }
            routes.clear();
        }

        // Generate Registrar on the final processing round or when models are collected
        if (!binaryModels.isEmpty() || !binaryRecords.isEmpty() || !sealedBases.isEmpty()) {
            try {
                generateRegistrar();
            } catch (IOException e) {
                env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to generate BinaryPackableRegistrar: " + e.getMessage());
            }
            // Clear to avoid generating it again in the next round
            binaryModels.clear();
            binaryRecords.clear();
            sealedBases.clear();
            serializerNames.clear();
            validatedModels.clear();
            clientWritableModels.clear();
            enumTypes.clear();
        }

        return true;
    }

    /**
     * Reads a sealed {@code @DataModel} base — a sealed interface or sealed abstract class — into
     * the permitted set the generated registrar will publish.
     *
     * <p>Everything refused here is refused because the receiver could not act on it safely. The
     * whole point of a sealed wire type is that the complete set of classes it may become is known
     * when the code is compiled, so a payload naming anything else can be turned away before it is
     * built.</p>
     */
    private void collectSealedBase(TypeElement base, Types typeUtils) {
        String baseFqcn = base.getQualifiedName().toString();
        List<String> permitted = new ArrayList<>();
        for (TypeMirror permittedType : base.getPermittedSubclasses()) {
            Element permittedElement = typeUtils.asElement(permittedType);
            if (!(permittedElement instanceof TypeElement)) {
                continue;
            }
            TypeElement sub = (TypeElement) permittedElement;
            String subFqcn = sub.getQualifiedName().toString();
            if (sub.getModifiers().contains(Modifier.SEALED)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    baseFqcn + " permits " + subFqcn + ", which is itself sealed. A sealed wire "
                    + "type has to be one level deep: a value on the wire names one base and the "
                    + "receiver looks up one permitted set, so a family of families has no single "
                    + "set to check against. Flatten it.", base);
                continue;
            }
            if (sub.getAnnotation(DataModel.class) == null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    baseFqcn + " permits " + subFqcn + ", which is not annotated @DataModel. Every "
                    + "class a sealed wire type permits must be a wire type itself, or a payload "
                    + "naming it could not be built.", base);
                continue;
            }
            if (sub.getModifiers().contains(Modifier.ABSTRACT)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    baseFqcn + " permits " + subFqcn + ", which is abstract. There is no value to "
                    + "build for it.", base);
                continue;
            }
            if (sub.getKind() != ElementKind.RECORD && !sub.getModifiers().contains(Modifier.FINAL)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    baseFqcn + " permits " + subFqcn + ", which is declared non-sealed. Classes "
                    + "outside the permitted set can then extend it, and the receiver would have "
                    + "no way to tell those apart from the ones it agreed to accept. Declare it "
                    + "final.", base);
                continue;
            }
            permitted.add(subFqcn);
        }
        if (permitted.isEmpty()) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                baseFqcn + " permits nothing that can cross the wire, so no value of it could ever "
                + "be sent.", base);
            return;
        }
        sealedBases.put(baseFqcn, permitted);
    }

    /**
     * Generates {@code <Record>_Serializer} for a {@code record} model: a {@code write} that puts
     * every component out in canonical order, and a {@code read} that pulls every component back
     * and <i>then</i> calls the canonical constructor.
     *
     * <p>That ordering is the whole difference from a class. A class is built empty and filled, so
     * the reader can hand out the instance before its fields arrive. A record's components are
     * final, so it cannot exist until the last one has been read.</p>
     */
    private void generateRecordSerializer(TypeElement typeElement, Types typeUtils) throws IOException {
        String packageName = getPackageName(typeElement);
        String className = typeElement.getSimpleName().toString();
        String serializerClassName = className + "_Serializer";
        String modelName = typeElement.getQualifiedName().toString();
        String serializerFqcn = packageName.isEmpty()
                ? serializerClassName : packageName + "." + serializerClassName;
        serializerNames.put(modelName, serializerFqcn);

        List<FieldInfo> components = getRecordComponents(typeElement);

        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(serializerFqcn);
        try (Writer writer = builderFile.openWriter()) {
            writer.write("package " + packageName + ";\n\n");
            writer.write("import java.nio.ByteBuffer;\n");
            writer.write("import com.zeroz4j.api.BinarySerializer;\n");
            writer.write("import com.zeroz4j.api.GrowableBuffer;\n");
            writer.write("import com.zeroz4j.api.ObjectMapper;\n\n");
            writer.write("// Auto-generated by zeroz4j APT — do not edit\n");
            writer.write("public class " + serializerClassName + " {\n\n");

            writer.write("    public static void write(" + modelName + " obj, GrowableBuffer buffer, ObjectMapper mapper) {\n");
            writer.write("        if (obj == null) {\n");
            writer.write("            buffer.put((byte) 0);\n");
            writer.write("            return;\n");
            writer.write("        }\n");
            writer.write("        buffer.put((byte) 1);\n");
            for (FieldInfo component : components) {
                collectEnumTypes(component.type);
                writeSerializationCode(writer, component, getReadExpression(component, "obj"));
            }
            writer.write("    }\n\n");

            writer.write("    public static " + modelName + " read(ByteBuffer buffer, ObjectMapper mapper) {\n");
            writer.write("        if (buffer.get() == 0) {\n");
            writer.write("            return null;\n");
            writer.write("        }\n");
            StringBuilder arguments = new StringBuilder();
            int index = 0;
            for (FieldInfo component : components) {
                String local = "_c" + index++;
                writer.write("        " + component.type + " " + local + " = "
                        + defaultValueFor(component.type) + ";\n");
                writeDeserializationCode(writer, component, local + " = ", "");
                if (arguments.length() > 0) {
                    arguments.append(", ");
                }
                arguments.append(local);
            }
            // Construct last: this is the line the whole record path exists for.
            writer.write("        return new " + modelName + "(" + arguments + ");\n");
            writer.write("    }\n");
            writer.write("}\n");
        }
    }

    /** The record's components, in canonical constructor order, read through their accessors. */
    private List<FieldInfo> getRecordComponents(TypeElement typeElement) {
        List<FieldInfo> components = new ArrayList<>();
        for (RecordComponentElement component : typeElement.getRecordComponents()) {
            components.add(new FieldInfo(
                    component.getSimpleName().toString(),
                    component.asType(),
                    component.getAccessor().getSimpleName().toString(),
                    null,
                    true));
        }
        return components;
    }

    /** The value a local of this type starts at, before the component's bytes are read into it. */
    private static String defaultValueFor(TypeMirror type) {
        switch (type.toString()) {
            case "int":     return "0";
            case "long":    return "0L";
            case "double":  return "0.0";
            case "float":   return "0.0f";
            case "boolean": return "false";
            case "short":   return "(short) 0";
            case "byte":    return "(byte) 0";
            case "char":    return "(char) 0";
            default:        return "null";
        }
    }

    private void generateSerializer(TypeElement typeElement, Types typeUtils) throws IOException {
        String packageName = getPackageName(typeElement);
        String className = typeElement.getSimpleName().toString();
        String serializerClassName = className + "_Serializer";
        serializerNames.put(typeElement.getQualifiedName().toString(),
                packageName.isEmpty() ? serializerClassName : packageName + "." + serializerClassName);

        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(packageName + "." + serializerClassName);
        try (Writer writer = builderFile.openWriter()) {
            writer.write("package " + packageName + ";\n\n");
            writer.write("import java.nio.ByteBuffer;\n");
            writer.write("import com.zeroz4j.api.BinarySerializer;\n");
            writer.write("import com.zeroz4j.api.GrowableBuffer;\n");
            writer.write("import com.zeroz4j.api.ObjectMapper;\n\n");
            writer.write("// Auto-generated by zeroz4j APT \u2014 do not edit\n");
            writer.write("public class " + serializerClassName + " {\n\n");

            // Write method
            writer.write("    public static void write(" + className + " obj, GrowableBuffer buffer, ObjectMapper mapper) {\n");
            writer.write("        if (obj == null) {\n");
            writer.write("            buffer.put((byte) 0);\n");
            writer.write("            return;\n");
            writer.write("        }\n");
            writer.write("        buffer.put((byte) 1);\n");

            List<FieldInfo> fields = getFields(typeElement, typeUtils);
            for (FieldInfo field : fields) {
                collectEnumTypes(field.type);
                String readExpr = getReadExpression(field, "obj");
                writeSerializationCode(writer, field, readExpr);
            }
            writer.write("    }\n\n");

            // Read method
            writer.write("    public static void read(" + className + " obj, ByteBuffer buffer, ObjectMapper mapper) {\n");
            writer.write("        if (buffer.get() == 0) {\n");
            writer.write("            return;\n");
            writer.write("        }\n");

            for (FieldInfo field : fields) {
                writeDeserializationCode(writer, field, "obj");
            }
            writer.write("    }\n");

            writer.write("}\n");
        }
    }

    private void writeSerializationCode(Writer writer, FieldInfo field, String readExpr) throws IOException {
        String typeStr = field.type.toString();
        if (typeStr.equals("int")) {
            writer.write("        buffer.putInt(" + readExpr + ");\n");
        } else if (typeStr.equals("long")) {
            writer.write("        buffer.putLong(" + readExpr + ");\n");
        } else if (typeStr.equals("double")) {
            writer.write("        buffer.putDouble(" + readExpr + ");\n");
        } else if (typeStr.equals("float")) {
            writer.write("        buffer.putFloat(" + readExpr + ");\n");
        } else if (typeStr.equals("boolean")) {
            writer.write("        buffer.put((byte) (" + readExpr + " ? 1 : 0));\n");
        } else if (typeStr.equals("short")) {
            writer.write("        buffer.putShort(" + readExpr + ");\n");
        } else if (typeStr.equals("byte")) {
            writer.write("        buffer.put(" + readExpr + ");\n");
        } else if (typeStr.equals("char")) {
            writer.write("        buffer.putChar(" + readExpr + ");\n");
        } else if (typeStr.equals("java.lang.String")) {
            writer.write("        BinarySerializer.writeString(buffer, " + readExpr + ");\n");
        } else if (isEnum(field.type)) {
            // TeaVM-safe scalar enum: write the constant name, no reflection, no registry tag.
            writer.write("        BinarySerializer.writeString(buffer, " + readExpr + " == null ? null : " + readExpr + ".name());\n");
        } else {
            // Everything else goes out through the tagged path, models included.
            //
            // A model-typed field used to be written straight into the buffer instead, which was
            // smaller by a few bytes and wrong in three ways. Nothing recorded what was already
            // being written, so two models referring to each other recursed until the stack ran
            // out. The same instance in two fields arrived as two objects. And the reader built the
            // nested value with a plain constructor, so a model nested inside a live-synced one
            // never became a tracked instance. The tagged path has always handled all three, which
            // is why a model reached through a List or an Object-typed field behaved correctly
            // while the same model in a declared field did not.
            //
            // What it costs is an id and a class name per nested model. Collections were already
            // paying that, so this is bounded by the number of model-typed fields, not by how much
            // data there is.
            writer.write("        BinarySerializer.writeValue(buffer, " + readExpr + ", mapper);\n");
        }
    }

    private void writeDeserializationCode(Writer writer, FieldInfo field, String objName) throws IOException {
        writeDeserializationCode(writer, field,
                getWriteStatementPrefix(field, objName), getWriteStatementSuffix(field));
    }

    /**
     * Emits the statements that read one field or record component and store it.
     *
     * @param writeTarget everything up to the value — a setter call opener, a field assignment, or
     *                    an assignment to a local while a record's components are being gathered
     * @param suffix      the closing bracket when {@code writeTarget} opened a setter call
     */
    private void writeDeserializationCode(Writer writer, FieldInfo field,
                                          String writeTarget, String suffix) throws IOException {
        String typeStr = field.type.toString();

        if (typeStr.equals("int")) {
            writer.write("        " + writeTarget + "buffer.getInt()" + suffix + ";\n");
        } else if (typeStr.equals("long")) {
            writer.write("        " + writeTarget + "buffer.getLong()" + suffix + ";\n");
        } else if (typeStr.equals("double")) {
            writer.write("        " + writeTarget + "buffer.getDouble()" + suffix + ";\n");
        } else if (typeStr.equals("float")) {
            writer.write("        " + writeTarget + "buffer.getFloat()" + suffix + ";\n");
        } else if (typeStr.equals("boolean")) {
            writer.write("        " + writeTarget + "(buffer.get() != 0)" + suffix + ";\n");
        } else if (typeStr.equals("short")) {
            writer.write("        " + writeTarget + "buffer.getShort()" + suffix + ";\n");
        } else if (typeStr.equals("byte")) {
            writer.write("        " + writeTarget + "buffer.get()" + suffix + ";\n");
        } else if (typeStr.equals("char")) {
            writer.write("        " + writeTarget + "buffer.getChar()" + suffix + ";\n");
        } else if (typeStr.equals("java.lang.String")) {
            writer.write("        " + writeTarget + "BinarySerializer.readString(buffer)" + suffix + ";\n");
        } else if (isEnum(field.type)) {
            // TeaVM-safe scalar enum: rebuild the constant via the known enum type's valueOf.
            writer.write("        {\n");
            writer.write("            String _raw = BinarySerializer.readString(buffer);\n");
            writer.write("            " + writeTarget + "(_raw == null ? null : " + typeStr + ".valueOf(_raw))" + suffix + ";\n");
            writer.write("        }\n");
        } else if (isSealedBase(field.type)) {
            // Only a class this sealed type permits may be built here; readSealed refuses anything
            // else before it reaches a constructor.
            writer.write("        " + writeTarget + "(" + typeStr + ") BinarySerializer.readSealed(buffer, mapper, \""
                    + sealedBaseNameOf(field.type) + "\")" + suffix + ";\n");
        } else {
            // Matches the tagged write above. The tag is what carries a value's identity, so a
            // loop closes on the same instance and a model sent twice arrives once.
            writer.write("        " + writeTarget + "(" + typeStr + ") BinarySerializer.readValue(buffer, mapper)" + suffix + ";\n");
        }
    }

    /**
     * Generates {@code <Model>_Rules} with one FieldRule factory per constrained field and
     * a whole-object validate method, when the model carries validation annotations.
     *
     * @return true if the model had any validation annotations and a Rules class was generated
     */
    private boolean generateRules(TypeElement typeElement, Types typeUtils) throws IOException {
        List<FieldInfo> constrained = new ArrayList<>();
        Map<String, VariableElement> fieldElements = new LinkedHashMap<>();
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) {
                continue;
            }
            VariableElement fieldVar = (VariableElement) enclosed;
            if (fieldVar.getAnnotation(NotBlank.class) == null
                    && fieldVar.getAnnotation(Size.class) == null
                    && fieldVar.getAnnotation(Min.class) == null
                    && fieldVar.getAnnotation(Max.class) == null) {
                continue;
            }
            String fName = fieldVar.getSimpleName().toString();
            TypeMirror fType = fieldVar.asType();
            String getter = findGetter(typeElement, fName, fType, typeUtils);
            constrained.add(new FieldInfo(fName, fType, getter,
                    findSetter(typeElement, fName, fType, typeUtils),
                    fieldVar.getModifiers().contains(Modifier.PRIVATE)));
            fieldElements.put(fName, fieldVar);
        }
        if (constrained.isEmpty()) {
            return false;
        }

        String packageName = getPackageName(typeElement);
        String className = typeElement.getSimpleName().toString();
        String rulesClassName = className + "_Rules";

        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(packageName + "." + rulesClassName);
        try (Writer writer = builderFile.openWriter()) {
            writer.write("package " + packageName + ";\n\n");
            writer.write("// Auto-generated by zeroz4j APT — do not edit\n");
            writer.write("public final class " + rulesClassName + " {\n\n");
            writer.write("    private " + rulesClassName + "() {}\n\n");

            for (FieldInfo field : constrained) {
                VariableElement fieldVar = fieldElements.get(field.name);
                String boxedType = getBoxedTypeName(field.type);
                writer.write("    public static com.zeroz4j.api.validation.FieldRule<" + boxedType + "> " + field.name + "() {\n");
                writer.write("        return value -> {\n");
                writer.write("            java.util.List<java.lang.String> violations = new java.util.ArrayList<>();\n");
                writeConstraintChecks(writer, fieldVar, field);
                writer.write("            return violations;\n");
                writer.write("        };\n");
                writer.write("    }\n\n");
            }

            writer.write("    public static java.util.List<java.lang.String> validate(" + className + " obj) {\n");
            writer.write("        java.util.List<java.lang.String> violations = new java.util.ArrayList<>();\n");
            writer.write("        if (obj == null) {\n");
            writer.write("            return violations;\n");
            writer.write("        }\n");
            for (FieldInfo field : constrained) {
                writer.write("        violations.addAll(" + field.name + "().validate(" + getReadExpression(field, "obj") + "));\n");
            }
            writer.write("        return violations;\n");
            writer.write("    }\n");
            writer.write("}\n");
        }
        return true;
    }

    private void writeConstraintChecks(Writer writer, VariableElement fieldVar, FieldInfo field) throws IOException {
        String fName = field.name;
        String typeStr = field.type.toString();
        boolean isString = typeStr.equals("java.lang.String");
        boolean isFloating = typeStr.equals("float") || typeStr.equals("double");

        NotBlank notBlank = fieldVar.getAnnotation(NotBlank.class);
        if (notBlank != null) {
            if (!isString) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@NotBlank only applies to String fields", fieldVar);
            } else {
                String msg = notBlank.message().isEmpty() ? fName + " must not be blank" : notBlank.message();
                writer.write("            if (value == null || value.trim().isEmpty()) {\n");
                writer.write("                violations.add(\"" + escape(msg) + "\");\n");
                writer.write("            }\n");
            }
        }

        Size size = fieldVar.getAnnotation(Size.class);
        if (size != null) {
            if (!isString) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@Size only applies to String fields", fieldVar);
            } else {
                String msg = size.message().isEmpty()
                        ? fName + " length must be between " + size.min() + " and " + size.max()
                        : size.message();
                writer.write("            if (value != null && (value.length() < " + size.min()
                        + " || value.length() > " + size.max() + ")) {\n");
                writer.write("                violations.add(\"" + escape(msg) + "\");\n");
                writer.write("            }\n");
            }
        }

        Min min = fieldVar.getAnnotation(Min.class);
        if (min != null) {
            if (isString) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@Min only applies to numeric fields", fieldVar);
            } else {
                String msg = min.message().isEmpty() ? fName + " must be at least " + min.value() : min.message();
                String accessor = isFloating ? "value.doubleValue() < " + min.value() + "d"
                                             : "value.longValue() < " + min.value() + "L";
                writer.write("            if (value != null && " + accessor + ") {\n");
                writer.write("                violations.add(\"" + escape(msg) + "\");\n");
                writer.write("            }\n");
            }
        }

        Max max = fieldVar.getAnnotation(Max.class);
        if (max != null) {
            if (isString) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@Max only applies to numeric fields", fieldVar);
            } else {
                String msg = max.message().isEmpty() ? fName + " must be at most " + max.value() : max.message();
                String accessor = isFloating ? "value.doubleValue() > " + max.value() + "d"
                                             : "value.longValue() > " + max.value() + "L";
                writer.write("            if (value != null && " + accessor + ") {\n");
                writer.write("                violations.add(\"" + escape(msg) + "\");\n");
                writer.write("            }\n");
            }
        }
    }

    private String getBoxedTypeName(TypeMirror type) {
        switch (type.toString()) {
            case "int": return "java.lang.Integer";
            case "long": return "java.lang.Long";
            case "double": return "java.lang.Double";
            case "float": return "java.lang.Float";
            case "short": return "java.lang.Short";
            case "byte": return "java.lang.Byte";
            case "boolean": return "java.lang.Boolean";
            case "char": return "java.lang.Character";
            default: return type.toString();
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Generates {@code <Model>_Live extends Model} for @ClientWritable models: setter
     * overrides report mutations to {@link com.zeroz4j.api.LiveMutationTracker}. The
     * client runtime instantiates this subclass during deserialization, so field writes
     * on live instances propagate to the server automatically.
     */
    private void generateLiveSubclass(TypeElement typeElement, Types typeUtils, boolean clientWritable)
            throws IOException {
        if (clientWritable && typeElement.getAnnotation(LiveSync.class) == null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "@ClientWritable on " + typeElement.getSimpleName()
                    + " without @LiveSync: client mutations would be sent to the server, but no state "
                    + "would ever come back, so the two tiers silently diverge. Add @LiveSync.",
                    typeElement);
        }

        String packageName = getPackageName(typeElement);
        String className = typeElement.getSimpleName().toString();
        String liveClassName = className + "_Live";

        List<FieldInfo> fields = getFields(typeElement, typeUtils);

        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(packageName + "." + liveClassName);
        try (Writer writer = builderFile.openWriter()) {
            writer.write("package " + packageName + ";\n\n");
            writer.write("// Auto-generated by zeroz4j APT — do not edit\n");
            writer.write("public class " + liveClassName + " extends " + className
                    + " implements com.zeroz4j.api.LiveObservable {\n\n");
            writer.write("    private final com.zeroz4j.signals.LiveSignal zeroz4jSignal =\n");
            writer.write("            new com.zeroz4j.signals.LiveSignal(this);\n\n");
            writer.write("    public " + liveClassName + "() {\n");
            writer.write("        super();\n");
            writer.write("    }\n\n");
            writer.write("    @Override\n");
            writer.write("    public void zeroz4jLiveRead() {\n");
            writer.write("        zeroz4jSignal.reportRead();\n");
            writer.write("    }\n\n");
            writer.write("    @Override\n");
            writer.write("    public void zeroz4jLiveChanged() {\n");
            writer.write("        zeroz4jSignal.notifyChanged();\n");
            writer.write("    }\n\n");

            for (FieldInfo field : fields) {
                // Getter override: reading a field inside an Effect or Computed makes this object a
                // tracked dependency, so an inbound sync re-renders without any subscribe call.
                if (field.getter != null) {
                    writer.write("    @Override\n");
                    writer.write("    public " + field.type.toString() + " " + field.getter + "() {\n");
                    writer.write("        zeroz4jLiveRead();\n");
                    writer.write("        return super." + field.getter + "();\n");
                    writer.write("    }\n\n");
                }

                if (!clientWritable) {
                    continue;
                }
                if (field.setter == null) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "@ClientWritable field '" + field.name + "' on " + className
                            + " has no setter, so edits to it would never reach the server. Setters are "
                            + "the tracking boundary: add one, or move the field out of the model.",
                            typeElement);
                    continue;
                }
                writer.write("    @Override\n");
                writer.write("    public void " + field.setter + "(" + field.type.toString() + " value) {\n");
                writer.write("        super." + field.setter + "(value);\n");
                writer.write("        com.zeroz4j.api.LiveMutationTracker.fieldChanged(this);\n");
                writer.write("    }\n\n");
            }

            writer.write("}\n");
        }
    }

    private void generateStub(TypeElement typeElement, Types typeUtils) throws IOException {
        String packageName = getPackageName(typeElement);
        String interfaceName = typeElement.getSimpleName().toString();
        String stubClassName = interfaceName + "_Stub";
        String fqcn = typeElement.getQualifiedName().toString();

        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(packageName + "." + stubClassName);
        try (Writer writer = builderFile.openWriter()) {
            writer.write("package " + packageName + ";\n\n");
            writer.write("import com.zeroz4j.api.RmiClientExecutor;\n\n");
            writer.write("// Auto-generated by zeroz4j APT \u2014 do not edit\n");
            writer.write("public class " + stubClassName + " implements " + fqcn + " {\n\n");

            // Methods (including those inherited from extended interfaces)
            for (ExecutableElement method : getServiceMethods(typeElement)) {
                String mName = method.getSimpleName().toString();
                TypeMirror retType = method.getReturnType();

                // Generate signature
                writer.write("    @Override\n");
                writer.write("    public " + retType.toString() + " " + mName + "(");
                List<? extends VariableElement> params = method.getParameters();
                for (int i = 0; i < params.size(); i++) {
                    VariableElement param = params.get(i);
                    writer.write(param.asType().toString() + " " + param.getSimpleName().toString());
                    if (i < params.size() - 1) {
                        writer.write(", ");
                    }
                }
                writer.write(") {\n");

                // Method body
                if (params.isEmpty()) {
                    writer.write("        Object[] args = new Object[0];\n");
                } else {
                    writer.write("        Object[] args = new Object[] { ");
                    for (int i = 0; i < params.size(); i++) {
                        writer.write(params.get(i).getSimpleName().toString());
                        if (i < params.size() - 1) {
                            writer.write(", ");
                        }
                    }
                    writer.write(" };\n");
                }

                String serviceName = fqcn;
                if (retType.toString().equals("void")) {
                    writer.write("        RmiClientExecutor.executeCall(\"" + serviceName + "\", \"" + mName + "\", args);\n");
                } else {
                    writer.write("        Object result = RmiClientExecutor.executeCall(\"" + serviceName + "\", \"" + mName + "\", args);\n");
                    writer.write("        return " + getCastExpression(retType, "result") + ";\n");
                }

                writer.write("    }\n\n");
            }

            writer.write("}\n");
        }
    }

    /**
     * Collects the abstract instance methods a service stub must implement: methods declared
     * on the interface itself plus those inherited from extended interfaces, excluding
     * static/default methods and {@code java.lang.Object} members, deduplicated by signature.
     */
    private List<ExecutableElement> getServiceMethods(TypeElement typeElement) {
        List<ExecutableElement> methods = new ArrayList<>();
        Set<String> seenSignatures = new HashSet<>();
        for (Element member : processingEnv.getElementUtils().getAllMembers(typeElement)) {
            if (member.getKind() != ElementKind.METHOD) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) member;
            if (method.getEnclosingElement().getKind() != ElementKind.INTERFACE) {
                continue; // skips java.lang.Object members
            }
            if (method.isDefault() || method.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            StringBuilder signature = new StringBuilder(method.getSimpleName());
            for (VariableElement param : method.getParameters()) {
                signature.append('#').append(param.asType().toString());
            }
            if (seenSignatures.add(signature.toString())) {
                methods.add(method);
            }
        }
        return methods;
    }

    /** Names an element kind the way a developer would, for a diagnostic they have to act on. */
    private static String describeKind(ElementKind kind) {
        switch (kind) {
            case RECORD:     return "A record";
            case INTERFACE:  return "An interface";
            case ENUM:       return "An enum";
            case ANNOTATION_TYPE: return "An annotation type";
            default:         return "A " + kind.toString().toLowerCase().replace('_', ' ');
        }
    }

    private static final String ROUTE_VIEW = "com.zeroz4j.client.router.RouteView";
    private static final String ROUTE_LAYOUT = "com.zeroz4j.client.router.RouteLayout";
    private static final String NO_LAYOUT = "com.zeroz4j.api.NoLayout";

    /**
     * Reads one {@code @Route} class into a {@link RouteEntry}, reporting at compile time anything
     * that would otherwise only show up as a route that silently never matches.
     */
    private void collectRoute(TypeElement typeElement, Types typeUtils) {
        RouteEntry entry = new RouteEntry();
        entry.fqcn = typeElement.getQualifiedName().toString();

        Route route = typeElement.getAnnotation(Route.class);
        entry.pattern = route.value();
        entry.label = route.label().isEmpty()
                ? defaultLabel(typeElement.getSimpleName().toString()) : route.label();
        entry.order = route.order();

        // An annotation member of type Class cannot be read directly during processing -- the class
        // may not be compiled yet -- so it arrives as a mirror via this exception.
        String layoutFqcn = null;
        try {
            route.layout();
        } catch (javax.lang.model.type.MirroredTypeException mirrored) {
            layoutFqcn = mirrored.getTypeMirror().toString();
        }
        entry.layoutFqcn = NO_LAYOUT.equals(layoutFqcn) ? null : layoutFqcn;

        RequiresRole requiresRole = typeElement.getAnnotation(RequiresRole.class);
        if (requiresRole != null) {
            entry.roles.addAll(Arrays.asList(requiresRole.value()));
        }

        boolean isView = implementsInterface(typeElement, typeUtils, ROUTE_VIEW);
        boolean isLayout = implementsInterface(typeElement, typeUtils, ROUTE_LAYOUT);
        if (!isView && !isLayout) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "@Route classes must implement RouteView (a view) or RouteLayout (chrome that wraps "
                + "one). Without either there is nothing for the router to render.", typeElement);
            return;
        }
        if (isView && isLayout) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "@Route class implements both RouteView and RouteLayout, so whether it renders a "
                + "child is ambiguous. Pick one.", typeElement);
            return;
        }
        entry.layout = isLayout;

        if (!hasAccessibleNoArgConstructor(typeElement)) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "@Route classes need a public no-argument constructor: the router creates them "
                + "without reflection, which the browser runtime does not have.", typeElement);
            return;
        }
        if (!entry.pattern.startsWith("/")) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "Route path '" + entry.pattern + "' must start with '/'.", typeElement);
            return;
        }
        routes.add(entry);
    }

    /** Strips a trailing {@code View}/{@code Layout} so the default navigation label reads well. */
    private static String defaultLabel(String simpleName) {
        if (simpleName.endsWith("View") && simpleName.length() > 4) {
            return simpleName.substring(0, simpleName.length() - 4);
        }
        if (simpleName.endsWith("Layout") && simpleName.length() > 6) {
            return simpleName.substring(0, simpleName.length() - 6);
        }
        return simpleName;
    }

    /** Walks the type hierarchy looking for an interface by name, generics erased. */
    private boolean implementsInterface(TypeElement typeElement, Types typeUtils, String interfaceName) {
        for (TypeMirror candidate : typeUtils.directSupertypes(typeElement.asType())) {
            String name = typeUtils.erasure(candidate).toString();
            if (interfaceName.equals(name)) {
                return true;
            }
            Element candidateElement = typeUtils.asElement(candidate);
            if (candidateElement instanceof TypeElement
                    && implementsInterface((TypeElement) candidateElement, typeUtils, interfaceName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAccessibleNoArgConstructor(TypeElement typeElement) {
        boolean sawConstructor = false;
        for (Element member : typeElement.getEnclosedElements()) {
            if (member.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            sawConstructor = true;
            ExecutableElement constructor = (ExecutableElement) member;
            if (constructor.getParameters().isEmpty()
                    && constructor.getModifiers().contains(Modifier.PUBLIC)) {
                return true;
            }
        }
        return !sawConstructor;   // no declared constructor means the default one
    }

    /**
     * Writes the route table: a registrar that adds every {@code @Route} in this module, plus its
     * ServiceLoader entry.
     */
    private void generateRouteRegistrar() throws IOException {
        String packageName = "com.zeroz4j.generated";
        String className = "RouteRegistrar_" + routeRegistrarSuffix();

        JavaFileObject builderFile =
            processingEnv.getFiler().createSourceFile(packageName + "." + className);
        try (Writer writer = builderFile.openWriter()) {
            writer.write("package " + packageName + ";\n\n");
            writer.write("import com.zeroz4j.client.router.RouteDefinition;\n");
            writer.write("import com.zeroz4j.client.router.RouteRegistrar;\n");
            writer.write("import com.zeroz4j.client.router.RouteRegistry;\n");
            writer.write("import java.util.LinkedHashSet;\n");
            writer.write("import java.util.Set;\n\n");
            writer.write("// Auto-generated by zeroz4j APT — do not edit\n");
            writer.write("public class " + className + " implements RouteRegistrar {\n");
            writer.write("    @Override\n");
            writer.write("    public void registerAll() {\n");
            for (RouteEntry entry : routes) {
                writer.write("        {\n");
                writer.write("            Set<String> roles = new LinkedHashSet<>();\n");
                for (String role : entry.roles) {
                    writer.write("            roles.add(\"" + role + "\");\n");
                }
                writer.write("            RouteRegistry.register(new RouteDefinition(\n");
                writer.write("                \"" + entry.pattern + "\",\n");
                writer.write("                \"" + entry.fqcn + "\",\n");
                writer.write("                " + (entry.layoutFqcn == null
                        ? "null" : "\"" + entry.layoutFqcn + "\"") + ",\n");
                writer.write("                roles,\n");
                writer.write("                " + entry.fqcn + "::new,\n");
                writer.write("                " + entry.layout + ",\n");
                writer.write("                \"" + entry.label + "\",\n");
                writer.write("                " + entry.order + "));\n");
                writer.write("        }\n");
            }
            writer.write("    }\n");
            writer.write("}\n");
        }

        try {
            javax.tools.FileObject resourceFile = processingEnv.getFiler().createResource(
                javax.tools.StandardLocation.CLASS_OUTPUT, "",
                "META-INF/services/com.zeroz4j.client.router.RouteRegistrar");
            try (Writer writer = resourceFile.openWriter()) {
                writer.write(packageName + "." + className + "\n");
            }
        } catch (IOException e) {
            // Already created in an earlier round.
        }
    }

    /** Unique-per-module suffix, so two modules' route registrars do not collide on one FQCN. */
    private String routeRegistrarSuffix() {
        java.util.TreeSet<String> all = new java.util.TreeSet<>();
        for (RouteEntry entry : routes) {
            all.add(entry.fqcn + "@" + entry.pattern);
        }
        long h = 1125899906842597L;
        for (String s : all) {
            for (int i = 0; i < s.length(); i++) {
                h = 31 * h + s.charAt(i);
            }
        }
        return Long.toHexString(h & 0x7fffffffffffffffL);
    }

    private void generateRegistrar() throws IOException {
        String packageName = "com.zeroz4j.generated";
        // Unique class name per module: every APT-generated registrar otherwise shares the FQCN
        // com.zeroz4j.generated.BinaryPackableRegistrar, so when more than one module on the
        // classpath has @DataModel types the classes collide and only one module's types register
        // (ServiceLoader loads a single class per name). Deriving the suffix from this module's
        // model set makes it unique and stable.
        String className = "BinaryPackableRegistrar_" + registrarSuffix();

        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(packageName + "." + className);
        try (Writer writer = builderFile.openWriter()) {
            writer.write("package " + packageName + ";\n\n");
            if (!binaryRecords.isEmpty()) {
                writer.write("import com.zeroz4j.api.BinaryRecordDelegate;\n");
            }
            writer.write("import com.zeroz4j.api.BinaryRegistry;\n");
            writer.write("import com.zeroz4j.api.BinaryRegistrar;\n");
            writer.write("import com.zeroz4j.api.BinarySerializerDelegate;\n");
            writer.write("import com.zeroz4j.api.GrowableBuffer;\n");
            writer.write("import com.zeroz4j.api.ObjectMapper;\n");
            writer.write("import java.nio.ByteBuffer;\n\n");
            writer.write("// Auto-generated by zeroz4j APT \u2014 do not edit\n");
            writer.write("public class " + className + " implements BinaryRegistrar {\n");
            writer.write("    @Override\n");
            writer.write("    public void registerAll() {\n");
            for (String model : validatedModels) {
                writer.write("        com.zeroz4j.api.validation.ValidationRegistry.register(\"" + model + "\",\n");
                writer.write("            obj -> " + model + "_Rules.validate((" + model + ") obj));\n");
            }
            for (String model : clientWritableModels) {
                // Both names, because the browser holds the subclass and the wire carries
                // the model: the writer needs the pair to put the model's own name on a
                // client edit.
                writer.write("        BinaryRegistry.registerLive(\"" + model + "\", \""
                        + model + "_Live\", " + model + "_Live::new);\n");
            }
            for (String enumType : enumTypes) {
                writer.write("        BinaryRegistry.registerEnum(\"" + enumType + "\", " + enumType + "::valueOf);\n");
            }
            for (Map.Entry<String, List<String>> sealed : sealedBases.entrySet()) {
                StringBuilder members = new StringBuilder();
                for (String member : sealed.getValue()) {
                    if (members.length() > 0) {
                        members.append(", ");
                    }
                    members.append('"').append(member).append('"');
                }
                writer.write("        BinaryRegistry.registerSealed(\"" + sealed.getKey()
                        + "\", new String[] { " + members + " });\n");
            }
            for (String model : binaryModels) {
                String serializer = serializerNames.getOrDefault(model, model + "_Serializer");
                writer.write("        BinaryRegistry.register(\"" + model + "\", " + model + "::new, new BinarySerializerDelegate<" + model + ">() {\n");
                writer.write("            @Override\n");
                writer.write("            public void write(" + model + " obj, GrowableBuffer buffer, ObjectMapper mapper) {\n");
                writer.write("                " + serializer + ".write(obj, buffer, mapper);\n");
                writer.write("            }\n");
                writer.write("            @Override\n");
                writer.write("            public void read(" + model + " obj, ByteBuffer buffer, ObjectMapper mapper) {\n");
                writer.write("                " + serializer + ".read(obj, buffer, mapper);\n");
                writer.write("            }\n");
                writer.write("        });\n");
            }
            for (String model : binaryRecords) {
                String serializer = serializerNames.getOrDefault(model, model + "_Serializer");
                // No supplier: there is no empty record to hand out, so the delegate returns the
                // finished value instead of filling one.
                writer.write("        BinaryRegistry.registerRecord(\"" + model + "\", new BinaryRecordDelegate<" + model + ">() {\n");
                writer.write("            @Override\n");
                writer.write("            public void write(" + model + " obj, GrowableBuffer buffer, ObjectMapper mapper) {\n");
                writer.write("                " + serializer + ".write(obj, buffer, mapper);\n");
                writer.write("            }\n");
                writer.write("            @Override\n");
                writer.write("            public " + model + " read(ByteBuffer buffer, ObjectMapper mapper) {\n");
                writer.write("                return " + serializer + ".read(buffer, mapper);\n");
                writer.write("            }\n");
                writer.write("        });\n");
            }
            writer.write("    }\n");
            writer.write("}\n");
        }
        
        try {
            javax.tools.FileObject resourceFile = processingEnv.getFiler().createResource(
                javax.tools.StandardLocation.CLASS_OUTPUT, "", "META-INF/services/com.zeroz4j.api.BinaryRegistrar");
            try (Writer writer = resourceFile.openWriter()) {
                writer.write(packageName + "." + className + "\n");
            }
        } catch (IOException e) {
            // Ignore if already created
        }
    }

    /** Stable, unique-per-module suffix from this module's model/enum set, so registrars don't collide. */
    private String registrarSuffix() {
        java.util.TreeSet<String> all = new java.util.TreeSet<>();
        all.addAll(binaryModels);
        all.addAll(binaryRecords);
        all.addAll(sealedBases.keySet());
        all.addAll(enumTypes);
        all.addAll(clientWritableModels);
        all.addAll(validatedModels);
        long h = 1125899906842597L;
        for (String s : all) {
            for (int i = 0; i < s.length(); i++) {
                h = 31 * h + s.charAt(i);
            }
        }
        return Long.toHexString(h & 0x7fffffffffffffffL);
    }

    private String getPackageName(TypeElement typeElement) {
        Element owner = typeElement.getEnclosingElement();
        while (owner != null && owner.getKind() != ElementKind.PACKAGE) {
            owner = owner.getEnclosingElement();
        }
        return owner != null ? ((PackageElement) owner).getQualifiedName().toString() : "";
    }

    /**
     * Types that cannot cross the wire, mapped to the advice that actually helps. Deliberately a
     * blocklist rather than an allowlist: a field typed {@code Object}, an interface or an abstract
     * class serializes fine because dispatch happens on the runtime type, so an allowlist would
     * reject valid programs.
     */
    private static final Map<String, String> UNSUPPORTED_FIELD_TYPES = new LinkedHashMap<>();

    static {
        String useInstant = "use java.time.Instant, which carries an unambiguous point in time";
        UNSUPPORTED_FIELD_TYPES.put("java.time.ZonedDateTime", useInstant
                + " (time-zone rules are not available on the client)");
        UNSUPPORTED_FIELD_TYPES.put("java.time.OffsetDateTime", useInstant);
        UNSUPPORTED_FIELD_TYPES.put("java.time.OffsetTime", useInstant);
        UNSUPPORTED_FIELD_TYPES.put("java.time.ZoneId", "store the zone id as a String");
        UNSUPPORTED_FIELD_TYPES.put("java.time.ZoneOffset", "store the offset as a String");
        UNSUPPORTED_FIELD_TYPES.put("java.time.Period",
                "use java.time.Duration, or store the period as a String");
        UNSUPPORTED_FIELD_TYPES.put("java.time.Year", "use an int");
        UNSUPPORTED_FIELD_TYPES.put("java.time.YearMonth", "use a java.time.LocalDate");
        UNSUPPORTED_FIELD_TYPES.put("java.time.MonthDay", "use a java.time.LocalDate");
        UNSUPPORTED_FIELD_TYPES.put("java.util.Date", "use java.time.Instant");
        UNSUPPORTED_FIELD_TYPES.put("java.util.Calendar", "use java.time.Instant");
        UNSUPPORTED_FIELD_TYPES.put("java.sql.Date", "use java.time.LocalDate");
        UNSUPPORTED_FIELD_TYPES.put("java.sql.Timestamp", "use java.time.Instant");
        UNSUPPORTED_FIELD_TYPES.put("java.io.File", "store the path as a String");
        UNSUPPORTED_FIELD_TYPES.put("java.nio.file.Path", "store the path as a String");

        // Collections rebuilt as ArrayList / LinkedHashSet / LinkedHashMap on the receiving side.
        // These concrete types are outside that hierarchy, so the generated cast fails at runtime.
        String declareAsInterface =
                "declare the field as the interface type instead -- collections arrive as ArrayList, "
                + "LinkedHashSet or LinkedHashMap, so this concrete type fails its cast";
        UNSUPPORTED_FIELD_TYPES.put("java.util.TreeSet", declareAsInterface + " (use Set)");
        UNSUPPORTED_FIELD_TYPES.put("java.util.TreeMap", declareAsInterface + " (use Map)");
        UNSUPPORTED_FIELD_TYPES.put("java.util.LinkedList", declareAsInterface + " (use List)");
        UNSUPPORTED_FIELD_TYPES.put("java.util.Vector", declareAsInterface + " (use List)");
        UNSUPPORTED_FIELD_TYPES.put("java.util.ArrayDeque", declareAsInterface + " (use List)");
        UNSUPPORTED_FIELD_TYPES.put("java.util.PriorityQueue", declareAsInterface + " (use List)");
        UNSUPPORTED_FIELD_TYPES.put("java.util.Stack", declareAsInterface + " (use List)");
    }

    /**
     * Rejects {@code @DataModel} fields whose type cannot be serialized, at compile time.
     *
     * <p>Without this the mistake surfaces at runtime, and on the event and shared-signal paths it
     * used to surface not at all. The check is conservative by design — only types known to break are
     * refused, so a field typed {@code Object} or an interface still compiles.</p>
     *
     * @param typeElement the model being processed
     * @param typeUtils   type utilities
     */
    /**
     * Refuses the two inheritance shapes whose fields cannot be carried honestly.
     *
     * <p>Both used to compile and lose data with no word said anywhere. The first is a base class
     * that is not itself a {@code @DataModel}: there is no way to know its fields belong on the
     * wire, so they were dropped. The second is a subclass redeclaring a field name the base
     * already uses: both would be written, and both read back through whichever accessor won.</p>
     */
    private void checkInheritance(TypeElement typeElement, Types typeUtils) {
        TypeElement base = superclassOf(typeElement);
        if (base != null && base.getAnnotation(DataModel.class) == null) {
            List<String> lost = new ArrayList<>();
            for (Element enclosed : base.getEnclosedElements()) {
                if (enclosed.getKind() != ElementKind.FIELD) {
                    continue;
                }
                Set<Modifier> mods = enclosed.getModifiers();
                if (!mods.contains(Modifier.STATIC) && !mods.contains(Modifier.TRANSIENT)) {
                    lost.add(enclosed.getSimpleName().toString());
                }
            }
            if (!lost.isEmpty()) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    typeElement.getQualifiedName() + " extends "
                    + base.getQualifiedName() + ", which is not a @DataModel, so its fields "
                    + lost + " cannot be carried and would go missing with nothing to explain it. "
                    + "Annotate " + base.getSimpleName() + " with @DataModel, or move those fields "
                    + "down into the subclass. A base class with no fields needs no annotation.",
                    typeElement);
            }
            return;
        }
        if (base == null) {
            return;
        }
        Map<String, String> seenNames = new LinkedHashMap<>();
        for (TypeElement level = base; level != null; level = superclassOf(level)) {
            if (level.getAnnotation(DataModel.class) == null) {
                break;
            }
            for (Element enclosed : level.getEnclosedElements()) {
                if (enclosed.getKind() != ElementKind.FIELD) {
                    continue;
                }
                Set<Modifier> mods = enclosed.getModifiers();
                if (mods.contains(Modifier.STATIC) || mods.contains(Modifier.TRANSIENT)) {
                    continue;
                }
                seenNames.put(enclosed.getSimpleName().toString(),
                        level.getQualifiedName().toString());
            }
        }
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) {
                continue;
            }
            Set<Modifier> mods = enclosed.getModifiers();
            if (mods.contains(Modifier.STATIC) || mods.contains(Modifier.TRANSIENT)) {
                continue;
            }
            String name = enclosed.getSimpleName().toString();
            String declaredBy = seenNames.get(name);
            if (declaredBy != null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    typeElement.getQualifiedName() + " declares a field '" + name
                    + "' that " + declaredBy + " already declares. Both would be written, and both "
                    + "read back through the same accessor, so one would overwrite the other. "
                    + "Rename one of them.", typeElement);
            }
        }
    }

    private void checkFieldTypes(TypeElement typeElement, Types typeUtils) {
        for (FieldInfo field : getFields(typeElement, typeUtils)) {
            TypeMirror type = field.type;

            // An array of anything but a primitive has no tag: byte[], int[], long[], double[],
            // float[], short[], char[] and boolean[] are supported, Object arrays are not.
            if (type.getKind() == TypeKind.ARRAY) {
                TypeMirror component = ((javax.lang.model.type.ArrayType) type).getComponentType();
                if (!component.getKind().isPrimitive()) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "@DataModel field '" + field.name + "' is an array of "
                            + component + ", which cannot be serialized. Use a List<" + component
                            + "> instead. Only arrays of primitives are supported.", typeElement);
                }
                continue;
            }

            String erased = typeUtils.erasure(type).toString();
            String advice = UNSUPPORTED_FIELD_TYPES.get(erased);
            if (advice != null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@DataModel field '" + field.name + "' has unsupported type " + erased
                        + ": " + advice + ".", typeElement);
            }
        }
    }

    /**
     * Every field this model puts on the wire: the ones it inherits from a {@code @DataModel} base
     * first, in base-to-subclass order, then its own.
     *
     * <p>Inherited fields used to be left out entirely, which meant the most ordinary refactor in
     * Java — moving what several models share up into a base class — silently stopped that data
     * arriving. A base class has no serializer of its own; what it declares is written as part of
     * each concrete model below it.</p>
     */
    private List<FieldInfo> getFields(TypeElement typeElement, Types typeUtils) {
        List<FieldInfo> fields = new ArrayList<>();
        TypeElement base = superclassOf(typeElement);
        if (base != null && base.getAnnotation(DataModel.class) != null) {
            fields.addAll(getFields(base, typeUtils));
        }
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                VariableElement fieldVar = (VariableElement) enclosed;
                Set<Modifier> mods = fieldVar.getModifiers();
                if (mods.contains(Modifier.STATIC) || mods.contains(Modifier.TRANSIENT)) {
                    continue;
                }

                String fName = fieldVar.getSimpleName().toString();
                TypeMirror fType = fieldVar.asType();

                // Check getters/setters
                String getter = findGetter(typeElement, fName, fType, typeUtils);
                String setter = findSetter(typeElement, fName, fType, typeUtils);

                fields.add(new FieldInfo(fName, fType, getter, setter, mods.contains(Modifier.PRIVATE)));
            }
        }
        return fields;
    }

    private String findGetter(TypeElement typeElement, String fieldName, TypeMirror fieldType, Types typeUtils) {
        String capitalized = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) enclosed;
                String mName = method.getSimpleName().toString();
                if (method.getParameters().isEmpty()) {
                    if (mName.equals("get" + capitalized) || mName.equals("is" + capitalized) || mName.equals(fieldName)) {
                        return mName;
                    }
                }
            }
        }
        TypeElement superElement = superclassOf(typeElement);
        return superElement == null ? null : findGetter(superElement, fieldName, fieldType, typeUtils);
    }

    /** The declared superclass as an element, or null at {@code java.lang.Object} and above. */
    private static TypeElement superclassOf(TypeElement typeElement) {
        TypeMirror superclass = typeElement.getSuperclass();
        if (!(superclass instanceof DeclaredType)) {
            return null;
        }
        Element element = ((DeclaredType) superclass).asElement();
        if (!(element instanceof TypeElement)
                || "java.lang.Object".equals(((TypeElement) element).getQualifiedName().toString())) {
            return null;
        }
        return (TypeElement) element;
    }

    private String findSetter(TypeElement typeElement, String fieldName, TypeMirror fieldType, Types typeUtils) {
        String capitalized = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) enclosed;
                String mName = method.getSimpleName().toString();
                if (method.getParameters().size() == 1) {
                    if (mName.equals("set" + capitalized) || mName.equals(fieldName)) {
                        return mName;
                    }
                }
            }
        }
        TypeElement superElement = superclassOf(typeElement);
        return superElement == null ? null : findSetter(superElement, fieldName, fieldType, typeUtils);
    }

    private String getReadExpression(FieldInfo field, String objName) {
        if (field.getter != null) {
            return objName + "." + field.getter + "()";
        } else if (!field.isPrivate) {
            return objName + "." + field.name;
        } else {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.WARNING,
                "Field '" + field.name + "' is private with no getter. "
                + "Generated serializer may not compile. Add a public getter.");
            return objName + "." + field.name;
        }
    }

    private String getWriteStatementPrefix(FieldInfo field, String objName) {
        if (field.setter != null) {
            return objName + "." + field.setter + "(";
        } else {
            return objName + "." + field.name + " = ";
        }
    }

    private String getWriteStatementSuffix(FieldInfo field) {
        if (field.setter != null) {
            return ")";
        } else {
            return "";
        }
    }

    /**
     * True when the declared type is a Java {@code enum}. Used to emit TeaVM-safe,
     * reflection-free enum (de)serialization at codegen time (the concrete enum type is known).
     */
    private boolean isEnum(TypeMirror type) {
        if (type instanceof DeclaredType) {
            return ((DeclaredType) type).asElement().getKind() == ElementKind.ENUM;
        }
        return false;
    }

    /**
     * Records every enum type reachable from a field's declared type, including enums nested in
     * generic containers ({@code List<MyEnum>}, {@code Map<UUID, MyEnum>}), so the generated
     * registrar can register a reflection-free resolver for each via {@code BinaryRegistry.registerEnum}.
     */
    private void collectEnumTypes(TypeMirror type, Set<String> out) {
        if (type instanceof DeclaredType) {
            DeclaredType declared = (DeclaredType) type;
            Element element = declared.asElement();
            if (element.getKind() == ElementKind.ENUM) {
                out.add(((TypeElement) element).getQualifiedName().toString());
            }
            for (TypeMirror arg : declared.getTypeArguments()) {
                collectEnumTypes(arg, out);
            }
        }
    }

    private void collectEnumTypes(TypeMirror type) {
        collectEnumTypes(type, enumTypes);
    }

    /**
     * True when the declared type is a sealed {@code @DataModel} base — a sealed interface or
     * sealed abstract class. Such a field holds one of a known set of classes, so it travels
     * through the tagged path carrying the base name rather than being written in place.
     */
    private boolean isSealedBase(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return false;
        }
        Element element = ((DeclaredType) type).asElement();
        return element.getKind() != ElementKind.RECORD
                && element.getModifiers().contains(Modifier.SEALED)
                && element.getAnnotation(DataModel.class) != null;
    }

    /** The FQCN of a sealed base, generics erased, as the registrar and the wire name it. */
    private String sealedBaseNameOf(TypeMirror type) {
        return ((TypeElement) ((DeclaredType) type).asElement()).getQualifiedName().toString();
    }

    /** True when the declared type is a {@code record} annotated {@code @DataModel}. */
    private boolean isRecordModel(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return false;
        }
        Element element = ((DeclaredType) type).asElement();
        return element.getKind() == ElementKind.RECORD
                && element.getAnnotation(DataModel.class) != null;
    }

    /**
     * The FQCN of the {@code _Serializer} generated for a model type.
     *
     * <p>Generated serializers are always top-level classes in the model's own package, so a nested
     * model {@code com.x.Outer.Item} gets {@code com.x.Item_Serializer}. Naming it from the model's
     * canonical name instead would produce {@code com.x.Outer.Item_Serializer}, which does not
     * exist.</p>
     */
    private String serializerFqcnFor(TypeMirror type) {
        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        String packageName = getPackageName(element);
        String simpleName = element.getSimpleName().toString();
        return packageName.isEmpty()
                ? simpleName + "_Serializer" : packageName + "." + simpleName + "_Serializer";
    }

    private boolean isDataModel(TypeMirror type) {
        if (type instanceof DeclaredType) {
            TypeElement typeElem = (TypeElement) ((DeclaredType) type).asElement();
            if (typeElem.getAnnotation(DataModel.class) != null) {
                return true;
            }
            TypeMirror superclass = typeElem.getSuperclass();
            if (superclass != null && !superclass.toString().equals("java.lang.Object")) {
                return isDataModel(superclass);
            }
        }
        return false;
    }

    private String getCastExpression(TypeMirror type, String varName) {
        String typeStr = type.toString();
        if (typeStr.equals("int")) {
            return varName + " != null ? (Integer) " + varName + " : 0";
        } else if (typeStr.equals("long")) {
            return varName + " != null ? (Long) " + varName + " : 0L";
        } else if (typeStr.equals("double")) {
            return varName + " != null ? (Double) " + varName + " : 0.0";
        } else if (typeStr.equals("float")) {
            return varName + " != null ? (Float) " + varName + " : 0.0f";
        } else if (typeStr.equals("boolean")) {
            return varName + " != null ? (Boolean) " + varName + " : false";
        } else if (typeStr.equals("short")) {
            return varName + " != null ? (Short) " + varName + " : (short) 0";
        } else if (typeStr.equals("byte")) {
            return varName + " != null ? (Byte) " + varName + " : (byte) 0";
        } else if (typeStr.equals("char")) {
            return varName + " != null ? (Character) " + varName + " : (char) 0";
        } else {
            return "(" + typeStr + ") " + varName;
        }
    }

    private static class FieldInfo {
        final String name;
        final TypeMirror type;
        final String getter;
        final String setter;
        final boolean isPrivate;

        FieldInfo(String name, TypeMirror type, String getter, String setter, boolean isPrivate) {
            this.name = name;
            this.type = type;
            this.getter = getter;
            this.setter = setter;
            this.isPrivate = isPrivate;
        }
    }
}
