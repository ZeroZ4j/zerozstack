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
package com.zeroz4j.ui.component.mixin;

import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.HasStyle;
import com.zeroz4j.ui.theme.Layer;

/**
 * A component that can be told how high above the page it floats.
 *
 * <p>Every component in this library that draws over the page carries this, and every one of them
 * comes with the right layer already set — a toast is on the toast layer, a menu is on the menu
 * layer. Change it only when your application really does stack things differently from the
 * library's assumption.</p>
 *
 * <pre>{@code
 * Toast saved = new Toast("Saved");
 * saved.setLayer(Layer.OVERLAY);   // this application wants its drawer over its messages
 * }</pre>
 *
 * <p>Setting a layer does two things: it puts the tier's stacking number on the element, and it
 * puts the tier's marker class on it — {@code zz-layer-toast} — so the tier is visible when
 * somebody inspects the page. Read {@link Layer} for the ordering, and for the one thing no
 * stacking number can beat: the browser's own top layer.</p>
 *
 * @param <T> the component type, returned so calls can be chained
 */
public interface HasLayer<T extends Component> extends HasStyle {

    /**
     * The element the layer is put on. Nearly always the component's own element, which is what
     * this returns by default; a component built from several elements overrides it to name the
     * one that actually floats.
     *
     * @return the element that carries the stacking number
     */
    default Component getLayerComponent() {
        return getComponent();
    }

    /**
     * Puts this component on the named layer, taking it off whichever one it was on.
     *
     * <p>Passing {@link Layer#PAGE} returns it to ordinary page content. Passing {@code null}
     * removes the stacking number altogether and leaves the stylesheet to decide.</p>
     *
     * @param layer the tier to put it on, or null to leave it to the stylesheet
     * @return this component
     */
    @SuppressWarnings("unchecked")
    default T setLayer(Layer layer) {
        applyTo(getLayerComponent(), layer);
        return (T) this;
    }

    /**
     * Puts {@code target} on {@code layer}, for a component built out of plain elements that does
     * not carry this mixin itself. Does exactly what {@link #setLayer(Layer)} does.
     *
     * @param target the component to put on the layer
     * @param layer  the tier, or null to remove the stacking number
     */
    static void applyTo(Component target, Layer layer) {
        for (Layer known : Layer.values()) {
            String current = target.getElement().getClassName();
            if (current != null && (" " + current + " ").contains(" " + known.getClassName() + " ")) {
                target.getElement().setClassName(
                        (" " + current + " ").replace(" " + known.getClassName() + " ", " ").trim());
            }
        }
        if (layer == null) {
            target.getElement().getStyle().removeProperty("z-index");
            return;
        }
        String current = target.getElement().getClassName();
        target.getElement().setClassName(
                current == null || current.isEmpty()
                        ? layer.getClassName()
                        : current + " " + layer.getClassName());
        target.getElement().getStyle().setProperty("z-index", String.valueOf(layer.getZIndex()));
    }

    /**
     * The layer {@code target} is on, or null if it is not on one.
     *
     * @param target the component to ask
     * @return its tier, or null
     */
    static Layer layerOf(Component target) {
        String current = target.getElement().getClassName();
        if (current == null || current.isEmpty()) {
            return null;
        }
        String padded = " " + current + " ";
        for (Layer known : Layer.values()) {
            if (padded.contains(" " + known.getClassName() + " ")) {
                return known;
            }
        }
        return null;
    }

    /**
     * The layer this component is on, or null if it has not been put on one.
     *
     * @return the current tier, or null
     */
    default Layer getLayer() {
        return layerOf(getLayerComponent());
    }
}
