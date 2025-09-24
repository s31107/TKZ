module GUI {
    requires java.desktop;
    requires APIUtils;
    exports TKZWindows;
    uses API.BackupStrategy;
}