module GUI {
    requires java.desktop;
    requires ProfileManagement;
    requires IconsStrategies;
    requires APIUtils;
    exports TKZWindows;
    uses API.BackupStrategy;
}