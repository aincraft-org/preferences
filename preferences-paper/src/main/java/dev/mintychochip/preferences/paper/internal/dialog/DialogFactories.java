package dev.mintychochip.preferences.paper.internal.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

/**
 * Central factory for Paper dialog construction and custom-click identifiers.
 *
 * <p>All dialog UI for this plugin is built here so Paper API drift is localized.
 * Custom-click keys use the {@code preferences} namespace and are routed by
 * {@link ClickRouter}.</p>
 */
public final class DialogFactories {

    /** Custom-click key for saving an edit dialog; payload read from input key {@code value}. */
    public static final Key KEY_SAVE = Key.key("preferences", "save");
    /** Custom-click key that closes the active dialog without persisting changes. */
    public static final Key KEY_CANCEL = Key.key("preferences", "cancel");
    /** Custom-click key for the previous page of a preference list. */
    public static final Key KEY_LIST_PREV = Key.key("preferences", "list_prev");
    /** Custom-click key for the next page of a preference list. */
    public static final Key KEY_LIST_NEXT = Key.key("preferences", "list_next");

    /**
     * Builds the custom-click key for opening an edit screen from a list button.
     *
     * @param index zero-based index within the current page slice
     * @return custom-click key for the edit button
     */
    public static Key editKey(int index) {
        return Key.key("preferences", "edit/" + index);
    }

    /**
     * Scrollable multi-action list for preference list screens.
     *
     * @param title dialog title
     * @param actions action buttons for the current page
     * @param exit exit button shown below the list
     * @return configured dialog
     */
    public static Dialog multiAction(Component title, List<ActionButton> actions, ActionButton exit) {
        return Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(title).build())
            .type(DialogType.multiAction(actions, exit, 1)));
    }

    /**
     * Read-only notice with a single Close action wired to {@link #KEY_CANCEL}.
     *
     * @param title dialog title
     * @param body message bodies
     * @return configured dialog
     */
    public static Dialog notice(Component title, List<io.papermc.paper.registry.data.dialog.body.DialogBody> body) {
        return Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(title).body(body).build())
            .type(DialogType.notice(
                ActionButton.builder(Component.text("Close"))
                    .action(DialogAction.customClick(KEY_CANCEL, null))
                    .build())));
    }

    /**
     * Edit dialog with one input bound to key {@code value}, plus Save and Cancel actions.
     *
     * <p>Save submits {@link #KEY_SAVE}; Cancel submits {@link #KEY_CANCEL}.</p>
     *
     * @param title dialog title
     * @param description body lines shown above the input
     * @param input editable field bound to response key {@code value}
     * @return configured dialog
     */
    public static Dialog editDialog(Component title, List<Component> description, DialogInput input) {
        return Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(title)
                .body(description.stream()
                    .map(io.papermc.paper.registry.data.dialog.body.DialogBody::plainMessage)
                    .toList())
                .inputs(List.of(input))
                .build())
            .type(DialogType.confirmation(
                ActionButton.builder(Component.text("Save"))
                    .action(DialogAction.customClick(KEY_SAVE, null))
                    .build(),
                ActionButton.builder(Component.text("Cancel"))
                    .action(DialogAction.customClick(KEY_CANCEL, null))
                    .build())));
    }

    /**
     * Creates a single-option row for boolean/enum edit inputs.
     *
     * @param id stored option id returned on save
     * @param display label shown in the option list
     * @param initial whether this row starts selected
     * @return Paper {@link SingleOptionDialogInput.OptionEntry}
     */
    public static SingleOptionDialogInput.OptionEntry optionEntry(String id, Component display, boolean initial) {
        return SingleOptionDialogInput.OptionEntry.create(id, display, initial);
    }

    private DialogFactories() {}
}
