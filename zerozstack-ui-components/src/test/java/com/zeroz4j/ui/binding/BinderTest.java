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
package com.zeroz4j.ui.binding;

import com.zeroz4j.api.validation.FieldRule;
import com.zeroz4j.signals.Signal;
import com.zeroz4j.ui.component.HasValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the write-through / buffered distinction, which is the part of {@link Binder} that is easiest
 * to get wrong, plus the listener lifecycle that keeps an unbound field from writing to a bean.
 */
class BinderTest {

    /** Minimal DOM-free field, so these tests run on the JVM without TeaVM. */
    static class FakeField<T> implements HasValue<T> {
        private T value;
        private final List<ValueChangeListener<T>> listeners = new ArrayList<>();

        @Override
        public T getValue() {
            return value;
        }

        @Override
        public void setValue(T value) {
            T old = this.value;
            this.value = value;
            for (ValueChangeListener<T> l : new ArrayList<>(listeners)) {
                l.valueChanged(new ValueChangeEvent<>(this, old, value, false));
            }
        }

        @Override
        public com.zeroz4j.api.Disposable bindValue(Signal<T> signal) {
            throw new UnsupportedOperationException("not needed for these tests");
        }

        @Override
        public com.zeroz4j.api.Disposable bindValueReadOnly(Signal<T> signal) {
            throw new UnsupportedOperationException("not needed for these tests");
        }

        @Override
        public void addValueChangeListener(ValueChangeListener<T> listener) {
            listeners.add(listener);
        }

        @Override
        public void removeValueChangeListener(ValueChangeListener<T> listener) {
            listeners.remove(listener);
        }

        int listenerCount() {
            return listeners.size();
        }
    }

    /** A field that does not support change listeners, to prove Binder fails loudly rather than silently. */
    static class ListenerlessField<T> implements HasValue<T> {
        private T value;

        @Override
        public T getValue() {
            return value;
        }

        @Override
        public void setValue(T value) {
            this.value = value;
        }

        @Override
        public com.zeroz4j.api.Disposable bindValue(Signal<T> signal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.zeroz4j.api.Disposable bindValueReadOnly(Signal<T> signal) {
            throw new UnsupportedOperationException();
        }
    }

    static class Person {
        private String name;

        Person(String name) {
            this.name = name;
        }

        String getName() {
            return name;
        }

        void setName(String name) {
            this.name = name;
        }
    }

    private static Binder<Person> binderFor(FakeField<String> field) {
        Binder<Person> binder = new Binder<>();
        binder.forField(field).bind(Person::getName, Person::setName);
        return binder;
    }

    @Test
    void setBeanIsWriteThrough() {
        FakeField<String> field = new FakeField<>();
        Binder<Person> binder = binderFor(field);
        Person person = new Person("Alice");

        binder.setBean(person);
        assertEquals("Alice", field.getValue(), "setBean should populate the field");

        field.setValue("Bob");
        assertEquals("Bob", person.getName(), "edits should be written straight to the bean");
        assertEquals(person, binder.getBean());
    }

    @Test
    void readBeanIsBuffered() {
        FakeField<String> field = new FakeField<>();
        Binder<Person> binder = binderFor(field);
        Person person = new Person("Alice");

        binder.readBean(person);
        assertEquals("Alice", field.getValue(), "readBean should populate the field");
        assertNull(binder.getBean(), "readBean must not bind the bean for write-through");

        field.setValue("Bob");
        assertEquals("Alice", person.getName(), "buffered edits must not reach the bean");

        assertTrue(binder.hasChanges());
    }

    @Test
    void writeBeanCommitsTheBuffer() throws ValidationException {
        FakeField<String> field = new FakeField<>();
        Binder<Person> binder = binderFor(field);
        Person person = new Person("Alice");

        binder.readBean(person);
        field.setValue("Bob");
        binder.writeBean(person);

        assertEquals("Bob", person.getName());
        assertFalse(binder.hasChanges(), "committing should reset the modified baseline");
    }

    @Test
    void readBeanReleasesAPreviouslySetBean() {
        FakeField<String> field = new FakeField<>();
        Binder<Person> binder = binderFor(field);
        Person bound = new Person("Alice");
        Person other = new Person("Carol");

        binder.setBean(bound);
        binder.readBean(other);          // switch to buffered mode
        field.setValue("Bob");

        assertEquals("Alice", bound.getName(),
                "after readBean, edits must not leak into the bean previously passed to setBean");
        assertEquals("Carol", other.getName(), "buffered edits must not reach the read bean either");
    }

    @Test
    void refreshFieldsDiscardsBufferedEdits() {
        FakeField<String> field = new FakeField<>();
        Binder<Person> binder = binderFor(field);

        binder.readBean(new Person("Alice"));
        field.setValue("Bob");
        binder.refreshFields();

        assertEquals("Alice", field.getValue(), "refreshFields should reload the last read values");
        assertFalse(binder.hasChanges());
    }

    @Test
    void requiredAndValidatorsBlockTheWrite() {
        FakeField<String> field = new FakeField<>();
        Binder<Person> binder = new Binder<>();
        binder.forField(field)
                .asRequired("Name is required")
                .bind(Person::getName, Person::setName);

        Person person = new Person("Alice");
        binder.setBean(person);

        field.setValue("");
        assertEquals("Alice", person.getName(), "an invalid value must not be written through");
        assertFalse(binder.validate().isOk());

        field.setValue("Bob");
        assertEquals("Bob", person.getName());
        assertTrue(binder.validate().isOk());
    }

    @Test
    void withRuleReusesGeneratedModelRules() {
        FieldRule<String> notBlank = value ->
                value == null || value.trim().isEmpty()
                        ? Collections.singletonList("must not be blank")
                        : Collections.emptyList();

        FakeField<String> field = new FakeField<>();
        Binder<Person> binder = new Binder<>();
        binder.forField(field).withRule(notBlank).bind(Person::getName, Person::setName);

        Person person = new Person("Alice");
        binder.setBean(person);

        field.setValue("   ");
        assertEquals("Alice", person.getName(), "a rule violation must block the write");
        assertEquals("must not be blank",
                binder.validate().getValidationErrors().get(0).getErrorMessage());
    }

    @Test
    void removeBindingDetachesTheListener() {
        FakeField<String> field = new FakeField<>();
        Binder<Person> binder = new Binder<>();
        Binding<Person, String> binding = binder.forField(field).bind(Person::getName, Person::setName);

        Person person = new Person("Alice");
        binder.setBean(person);
        assertEquals(1, field.listenerCount());

        binder.removeBinding(binding);
        assertEquals(0, field.listenerCount(), "unbinding should detach the write-through listener");

        field.setValue("Bob");
        assertEquals("Alice", person.getName(), "an unbound field must no longer update the bean");
    }

    @Test
    void bindingAFieldWithoutListenerSupportFailsLoudly() {
        Binder<Person> binder = new Binder<>();
        assertThrows(UnsupportedOperationException.class,
                () -> binder.forField(new ListenerlessField<String>())
                        .bind(Person::getName, Person::setName),
                "a silent no-op would make setBean appear to work while never writing to the bean");
    }
}
