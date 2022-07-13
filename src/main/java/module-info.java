module com.mws.ospf {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.kordamp.bootstrapfx.core;
    requires org.jetbrains.annotations;
    requires java.xml;
    requires inet.ipaddr;
    requires org.jnrproject.ffi;

    exports com.mws.ospf;
    opens com.mws.ospf to javafx.fxml, javafx.graphics;
}
