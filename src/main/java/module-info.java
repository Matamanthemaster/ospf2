module com.mws.prototype.ospf2 {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.kordamp.bootstrapfx.core;
    requires org.jetbrains.annotations;
    requires java.xml;
    requires inet.ipaddr;

    exports com.mws.prototype.ospf2;
    opens com.mws.prototype.ospf2 to javafx.fxml, javafx.graphics;
    exports com.mws.prototype.ospf2.storage;
    opens com.mws.prototype.ospf2.storage to javafx.fxml, javafx.graphics;
}
