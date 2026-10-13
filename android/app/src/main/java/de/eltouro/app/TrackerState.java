package de.eltouro.app;

/** State shared by the plugin (called from the web page) and the foreground service. */
final class TrackerState {
    static volatile String baseUrl = "";
    static volatile String csrf = "";
    static volatile long session = 0;            // recording session of the server; 0 = no recording, only position events
    static volatile TrackerPlugin plugin = null;
    static volatile TrackerService service = null;
    static volatile String title = "ElTouro";
    static volatile String text = "";

    private TrackerState() {}
}
