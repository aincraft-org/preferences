package dev.jlo.preferences.internal.dialog;

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
 * ALL Paper Dialog API construction for this plugin lives here.
 * If a Paper API signature differs from this file, fix it HERE only.
 */
public final class DialogFactories {

    public static final Key KEY_SAVE = Key.key("preferences", "save");
    public static final Key KEY_CANCEL = Key.key("preferences", "cancel");
    public static final Key KEY_LIST_PREV = Key.key("preferences", "list_prev");
    public static final Key KEY_LIST_NEXT = Key.key("preferences", "list_next");

    public static Key editKey(int index) {
        return Key.key("preferences", "edit/" + index);
    }

    /** Scrollable button list (preferences list screens). */
    public static Dialog multiAction(Component title, List<ActionButton> actions, ActionButton exit) {
        return Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(title).build())
            .type(DialogType.multiAction(actions, exit, 1)));
    }

    /** Notice dialog with a single Close action (read-only preference view). */
    public static Dialog notice(Component title, List<io.papermc.paper.registry.data.dialog.body.DialogBody> body) {
        return Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(title).body(body).build())
            .type(DialogType.notice(
                ActionButton.builder(Component.text("Close"))
                    .action(DialogAction.customClick(KEY_CANCEL, null))
                    .build())));
    }

    /** Edit dialog: one input + Save (custom click) + Cancel. */
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

    /** PROBE RESULT: SingleOptionDialogInput.OptionEntry has no `of` factory.
     *  Verified against paper-api 1.21.7-R0.1-SNAPSHOT bytecode: the only
     *  factory is `create(String id, Component display, boolean initial)`
     *  (same arity/types as the original probe). Fixed here. */
    public static SingleOptionDialogInput.OptionEntry optionEntry(String id, Component display, boolean initial) {
        return SingleOptionDialogInput.OptionEntry.create(id, display, initial);
    }

    private DialogFactories() {}
}
