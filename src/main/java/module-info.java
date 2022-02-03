module com.mws.prototype.ospf2 {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.kordamp.bootstrapfx.core;
    requires org.jetbrains.annotations;
    requires java.xml;
    requires inet.ipaddr;

    exports com.mws.dplp;
    opens com.mws.dplp to javafx.fxml, javafx.graphics;
}
