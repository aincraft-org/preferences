package dev.mintychochip.preferences.internal.session;

import static org.junit.jupiter.api.Assertions.*;

import dev.mintychochip.preferences.api.PreferenceKey;
import dev.mintychochip.preferences.api.PreferenceScope;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DialogSessionManagerTest {

  private static DialogSession listSession(UUID player, PreferenceScope scope, int page) {
    return new DialogSession(
        player,
        DialogSession.Screen.PLUGIN_LIST,
        scope,
        page,
        null,
        null,
        null,
        null,
        List.of(),
        List.of());
  }

  private static DialogSession editSession(
      UUID player, PreferenceKey key, DialogSession.ParentContext parent) {
    return new DialogSession(
        player,
        DialogSession.Screen.EDIT,
        parent.scope(),
        parent.page(),
        parent.namespace(),
        parent.query(),
        key,
        parent,
        List.of(),
        List.of());
  }

  @Test
  void openAndQuery() {
    DialogSessionManager mgr = new DialogSessionManager();
    UUID p = UUID.randomUUID();
    mgr.open(listSession(p, PreferenceScope.PLAYER, 0));
    assertNotNull(mgr.current(p));
    assertTrue(mgr.matches(p, DialogSession.Screen.PLUGIN_LIST));
  }

  @Test
  void openingReplacesPreviousSession() {
    DialogSessionManager mgr = new DialogSessionManager();
    UUID p = UUID.randomUUID();
    mgr.open(listSession(p, PreferenceScope.PLAYER, 2));
    PreferenceKey key = new PreferenceKey("demo", "flag");
  var parent =
        new DialogSession.ParentContext(
            DialogSession.Screen.PLUGIN_LIST, PreferenceScope.PLAYER, null, null, 2);
    mgr.open(editSession(p, key, parent));
    assertEquals(DialogSession.Screen.EDIT, mgr.current(p).screen());
    assertEquals(key, mgr.current(p).editTarget());
    assertEquals(parent, mgr.current(p).parent());
  }

  @Test
  void unknownPlayerHasNoSession() {
    DialogSessionManager mgr = new DialogSessionManager();
    assertNull(mgr.current(UUID.randomUUID()));
    assertFalse(mgr.matches(UUID.randomUUID(), DialogSession.Screen.EDIT));
  }

  @Test
  void closeRemoves() {
    DialogSessionManager mgr = new DialogSessionManager();
    UUID p = UUID.randomUUID();
    mgr.open(listSession(p, PreferenceScope.PLAYER, 0));
    mgr.close(p);
    assertNull(mgr.current(p));
    assertFalse(mgr.matches(p, DialogSession.Screen.PLUGIN_LIST));
  }

  @Test
  void nullArgumentsRejected() {
    DialogSessionManager mgr = new DialogSessionManager();
    NullPointerException e = assertThrows(NullPointerException.class, () -> mgr.open(null));
    assertEquals("session", e.getMessage());
    e = assertThrows(NullPointerException.class, () -> mgr.current(null));
    assertEquals("player", e.getMessage());
    e = assertThrows(NullPointerException.class, () -> mgr.close(null));
    assertEquals("player", e.getMessage());
    e = assertThrows(NullPointerException.class, () -> mgr.matches(null, DialogSession.Screen.EDIT));
    assertEquals("player", e.getMessage());
    e = assertThrows(NullPointerException.class, () -> mgr.matches(UUID.randomUUID(), null));
    assertEquals("screen", e.getMessage());
  }

  @Test
  void closeForNamespaceClosesOnlyMatchingEditSessions() {
    DialogSessionManager mgr = new DialogSessionManager();
    UUID p1 = UUID.randomUUID();
    UUID p2 = UUID.randomUUID();
    UUID p3 = UUID.randomUUID();
    var parent =
        new DialogSession.ParentContext(
            DialogSession.Screen.PLUGIN_LIST, PreferenceScope.PLAYER, null, null, 0);
    mgr.open(editSession(p1, new PreferenceKey("demo", "flag"), parent));
    mgr.open(
        editSession(
            p2,
            new PreferenceKey("other", "flag"),
            new DialogSession.ParentContext(
                DialogSession.Screen.PLUGIN_LIST, PreferenceScope.PLAYER, null, null, 0)));
    mgr.open(listSession(p3, PreferenceScope.PLAYER, 0));
    mgr.closeForNamespace("demo");
    assertNull(mgr.current(p1));
    assertNotNull(mgr.current(p2));
    assertNotNull(mgr.current(p3));
  }

  @Test
  void displayedSnapshotsAreImmutable() {
    List<PreferenceKey> items = new ArrayList<>();
    items.add(new PreferenceKey("demo", "flag"));
    List<String> namespaces = new ArrayList<>();
    namespaces.add("demo");

    DialogSession session =
        new DialogSession(
            UUID.randomUUID(),
            DialogSession.Screen.HOME,
            PreferenceScope.PLAYER,
            0,
            null,
            null,
            null,
            null,
            items,
            namespaces);

    items.add(new PreferenceKey("demo", "other"));
    namespaces.add("other");

    assertEquals(List.of(new PreferenceKey("demo", "flag")), session.displayedItems());
    assertEquals(List.of("demo"), session.displayedNamespaces());
    assertThrows(
        UnsupportedOperationException.class, () -> session.displayedItems().add(new PreferenceKey("x", "y")));
    assertThrows(UnsupportedOperationException.class, () -> session.displayedNamespaces().add("x"));
  }

  @Test
  void editTargetRequiredForEditScreen() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new DialogSession(
                    UUID.randomUUID(),
                    DialogSession.Screen.EDIT,
                    PreferenceScope.PLAYER,
                    0,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of()));
    assertEquals("editTarget is required for edit sessions", e.getMessage());
  }

  @Test
  void nonEditScreensAcceptNullEditTarget() {
    DialogSession session =
        new DialogSession(
            UUID.randomUUID(),
            DialogSession.Screen.SEARCH_RESULTS,
            PreferenceScope.GLOBAL,
            1,
            "demo",
            "flag",
            null,
            null,
            List.of(new PreferenceKey("demo", "flag")),
            List.of());
    assertNull(session.editTarget());
    assertEquals("flag", session.query());
    assertEquals("demo", session.namespace());
  }

  @Test
  void editSessionRetainsParentContext() {
    UUID player = UUID.randomUUID();
    PreferenceKey key = new PreferenceKey("demo", "flag");
    var parent =
        new DialogSession.ParentContext(
            DialogSession.Screen.SEARCH_RESULTS,
            PreferenceScope.PLAYER,
            "demo",
            "flag",
            3);
    DialogSession session = editSession(player, key, parent);

    assertEquals(DialogSession.Screen.EDIT, session.screen());
    assertEquals(parent, session.parent());
    assertEquals(DialogSession.Screen.SEARCH_RESULTS, session.parent().screen());
    assertEquals("demo", session.parent().namespace());
    assertEquals("flag", session.parent().query());
    assertEquals(3, session.parent().page());
  }
}
