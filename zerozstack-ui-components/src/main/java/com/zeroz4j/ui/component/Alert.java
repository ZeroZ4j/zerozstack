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
package com.zeroz4j.ui.component;

import com.zeroz4j.ui.component.mixin.HasColorVariants;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.theme.ThemeColor;

/**
 * A tinted strip of prose the reader is meant to act on.
 *
 * <p>This is what a screen reaches for when it has something to say that is not a field, a button
 * or a row of data: a warning above a form, the reason a save failed, the note that a setting only
 * takes effect after a restart. An application built on 0.7.0 hand-built its own version of this
 * twice, in two files, because the one here could only be a line of text in a coloured box - no
 * heading, no button, and the colour had to be spelled out as a stylesheet class name.</p>
 *
 * <p>A notice has a <b>tone</b>, said in ordinary words, and optionally a heading and one action:</p>
 *
 * <pre>{@code
 * add(Alert.caution("The disk is nearly full."));
 *
 * add(Alert.danger("Nothing was saved.")
 *          .withHeading("The upload failed")
 *          .withAction("Try again", e -> upload()));
 * }</pre>
 *
 * <p>The four tones are {@link Tone#INFORMATION}, {@link Tone#SUCCESS}, {@link Tone#CAUTION} and
 * {@link Tone#DANGER}, and each has a factory method of its own. A notice carries a small mark on
 * the left saying which tone it is, so the four are still told apart by somebody who cannot
 * separate the colours; {@link #setIconVisible(boolean)} takes it away.</p>
 *
 * <p>The strip announces itself to a screen reader as a notice, and long text wraps rather than
 * running off the side.</p>
 */
public class Alert extends Component implements HasComponents, HasText, HasStyle, HasSize,
        HasColorVariants<Alert> {

    /**
     * What kind of thing the notice is saying, in the reader's terms rather than a colour name.
     */
    public enum Tone {
        /** Something worth knowing. Nothing is wrong and nothing needs doing. */
        INFORMATION("alert-info"),
        /** Something the reader asked for has happened. */
        SUCCESS("alert-success"),
        /** Something is heading for trouble and there is still time to act. */
        CAUTION("alert-warning"),
        /** Something has failed, or is about to destroy something. */
        DANGER("alert-error");

        private final String className;

        Tone(String className) {
            this.className = className;
        }

        /**
         * Returns the stylesheet class this tone puts on the notice.
         *
         * @return the class name
         */
        public String getClassName() {
            return className;
        }
    }

    private final Div row = new Div();
    private final Div body = new Div();
    private final Div heading = new Div();
    private final Div message = new Div();
    private final Div iconSlot = new Div();
    private final Div actionSlot = new Div();

    private Tone tone;
    private boolean iconVisible = true;

    /** Creates an empty notice with no tone - a plain box until {@link #setTone} is called. */
    public Alert() {
        super("div");
        addClassName("alert alert-soft grid-cols-1 justify-items-stretch");
        getElement().setAttribute("role", "status");

        row.addClassName("flex w-full items-start gap-3 text-left");
        iconSlot.addClassName("shrink-0 mt-0.5 hidden");
        body.addClassName("min-w-0 flex-1 whitespace-normal break-words");
        heading.addClassName("font-semibold leading-snug hidden");
        message.addClassName("leading-relaxed");
        actionSlot.addClassName("shrink-0 self-center hidden");

        body.add(heading, message);
        row.add(iconSlot, body, actionSlot);
        getElement().appendChild(row.getElement());
    }

    /**
     * Creates a notice saying this, with no tone yet.
     *
     * @param text the words the reader reads
     */
    public Alert(String text) {
        this();
        setText(text);
    }

    /**
     * Creates a notice and puts a stylesheet class on it by name.
     *
     * @param text the words the reader reads
     * @param typeClassName a DaisyUI class such as {@code alert-info}
     * @deprecated Say the tone instead: {@link #info}, {@link #success}, {@link #caution},
     *             {@link #danger} or {@link #setTone}. Spelling out a class name puts the
     *             stylesheet back into application code, and nothing checks the spelling.
     */
    @Deprecated
    public Alert(String text, String typeClassName) {
        this(text);
        addClassName(typeClassName);
    }

    /**
     * A notice saying something worth knowing.
     *
     * @param text the words the reader reads
     * @return the new notice
     */
    public static Alert info(String text) {
        return new Alert(text).withTone(Tone.INFORMATION);
    }

    /**
     * A notice saying something has worked.
     *
     * @param text the words the reader reads
     * @return the new notice
     */
    public static Alert success(String text) {
        return new Alert(text).withTone(Tone.SUCCESS);
    }

    /**
     * A notice warning that something is heading for trouble.
     *
     * @param text the words the reader reads
     * @return the new notice
     */
    public static Alert caution(String text) {
        return new Alert(text).withTone(Tone.CAUTION);
    }

    /**
     * A notice saying something has failed, or is about to destroy something.
     *
     * @param text the words the reader reads
     * @return the new notice
     */
    public static Alert danger(String text) {
        return new Alert(text).withTone(Tone.DANGER);
    }

    /**
     * Sets what kind of thing this notice is saying.
     *
     * @param newTone the tone, or null for a plain untinted box
     */
    public void setTone(Tone newTone) {
        for (Tone t : Tone.values()) {
            removeClassName(t.getClassName());
        }
        this.tone = newTone;
        if (newTone != null) {
            addClassName(newTone.getClassName());
        }
        // A danger notice interrupts a screen reader; the other three wait their turn.
        getElement().setAttribute("role", newTone == Tone.DANGER ? "alert" : "status");
        refreshIcon();
    }

    /**
     * Sets the tone and returns the notice, so it reads inside the expression that creates it.
     *
     * @param newTone the tone
     * @return this notice
     */
    public Alert withTone(Tone newTone) {
        setTone(newTone);
        return this;
    }

    /**
     * Returns the tone, or null when the notice has none.
     *
     * @return the tone
     */
    public Tone getTone() {
        return tone;
    }

    /**
     * Sets a short bold line above the message. Pass null to take it away again.
     *
     * @param text the heading
     */
    public void setHeading(String text) {
        heading.setText(text == null ? "" : text);
        show(heading, text != null && !text.isEmpty());
    }

    /**
     * Sets the heading and returns the notice.
     *
     * @param text the heading
     * @return this notice
     */
    public Alert withHeading(String text) {
        setHeading(text);
        return this;
    }

    /**
     * Returns the heading, or an empty string when there is none.
     *
     * @return the heading
     */
    public String getHeading() {
        return heading.getText();
    }

    @Override
    public void setText(String text) {
        message.setText(text == null ? "" : text);
    }

    @Override
    public String getText() {
        return message.getText();
    }

    /**
     * Puts one button on the right of the notice - the thing to do about what it says.
     *
     * <p>Calling this again replaces the button; passing a null or empty label takes it away.</p>
     *
     * @param label the words on the button
     * @param listener what to do when it is pressed
     * @return the button, in case it needs styling or disabling later, or null when the label was
     *         null or empty
     */
    public Button setAction(String label, EventListener<ClickEvent<Button>> listener) {
        actionSlot.removeAll();
        if (label == null || label.isEmpty()) {
            show(actionSlot, false);
            return null;
        }
        Button button = new Button(label);
        button.addClassName("btn-sm");
        if (listener != null) {
            button.addClickListener(listener);
        }
        actionSlot.add(button);
        show(actionSlot, true);
        return button;
    }

    /**
     * Sets the action and returns the notice, so it reads inside the expression that creates it.
     *
     * @param label the words on the button
     * @param listener what to do when it is pressed
     * @return this notice
     */
    public Alert withAction(String label, EventListener<ClickEvent<Button>> listener) {
        setAction(label, listener);
        return this;
    }

    /**
     * Shows or hides the small mark on the left that says which tone this is.
     *
     * <p>It is shown by default, and it is what tells the four tones apart for a reader who
     * cannot separate the colours.</p>
     *
     * @param visible false to take the mark away
     */
    public void setIconVisible(boolean visible) {
        this.iconVisible = visible;
        refreshIcon();
    }

    /**
     * Says whether the tone mark is shown.
     *
     * @return true when it is
     */
    public boolean isIconVisible() {
        return iconVisible;
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public String getThemePrefix() {
        return "alert";
    }

    /**
     * Sets the colour by DaisyUI name.
     *
     * @param color the theme colour
     * @return this notice
     * @deprecated Say the tone instead - {@link #setTone} - which is the same four colours in
     *             words a reader would use, and brings the tone mark with it.
     */
    @Override
    @Deprecated
    public Alert setThemeColor(ThemeColor color) {
        return HasColorVariants.super.setThemeColor(color);
    }

    private void refreshIcon() {
        boolean wanted = iconVisible && tone != null;
        if (wanted) {
            iconSlot.getElement().setInnerHTML(iconFor(tone));
        } else {
            iconSlot.getElement().setInnerHTML("");
        }
        show(iconSlot, wanted);
    }

    private static void show(Div part, boolean visible) {
        if (visible) {
            part.removeClassName("hidden");
        } else {
            part.addClassName("hidden");
        }
    }

    /** One 20-pixel outline mark per tone, drawn in the text colour so it follows the theme. */
    private static String iconFor(Tone tone) {
        String path = switch (tone) {
            case INFORMATION -> "M12 8h.01M11 12h1v4h1M12 3a9 9 0 110 18 9 9 0 010-18z";
            case SUCCESS -> "M8.5 12.5l2.5 2.5 4.5-5M12 3a9 9 0 110 18 9 9 0 010-18z";
            case CAUTION -> "M12 9v4m0 4h.01M10.3 4.3L2.6 17.3a1.8 1.8 0 001.6 2.7h15.6a1.8 1.8 0 "
                + "001.6-2.7L13.7 4.3a1.8 1.8 0 00-3.4 0z";
            case DANGER -> "M15 9l-6 6m0-6l6 6M12 3a9 9 0 110 18 9 9 0 010-18z";
        };
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" class=\"h-5 w-5\" fill=\"none\""
            + " viewBox=\"0 0 24 24\" stroke=\"currentColor\" stroke-width=\"2\""
            + " aria-hidden=\"true\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\""
            + " d=\"" + path + "\"/></svg>";
    }
}
