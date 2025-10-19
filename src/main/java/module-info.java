module org.example.zoom {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.desktop;
    requires javafx.swing;
    requires java.sql;
    requires javafx.media;

    // Add these for automatic modules (remove if causing issues)
    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires mysql.connector.j;

    // Open packages to unnamed module for Java-WebSocket
    opens org.example.zoom to javafx.fxml;
    opens org.example.zoom.websocket to javafx.fxml;

    exports org.example.zoom;
    exports org.example.zoom.websocket;
}