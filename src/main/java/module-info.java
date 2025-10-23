module org.example.zoom {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.desktop;
    requires javafx.swing;
    requires java.sql;
    requires javafx.media;
    requires java.net.http;

    // Optional external libs
    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires mysql.connector.j;
    requires webcam.capture;

    opens org.example.zoom to javafx.fxml;
    opens org.example.zoom.websocket to javafx.fxml;

    exports org.example.zoom;
    exports org.example.zoom.websocket;
}
