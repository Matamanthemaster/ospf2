module com.mws.prototype.ospf2 {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.kordamp.bootstrapfx.core;
    requires org.jetbrains.annotations;
    requires java.xml;
    requires inet.ipaddr;
    
    exports com.mws.ospf2.ui;
    opens com.mws.ospf2.ui to javafx.fxml, javafx.graphics;
}
