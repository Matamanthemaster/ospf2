module com.mws.prototype.ospf2 {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.kordamp.bootstrapfx.core;

    opens com.mws.prototype.ospf2 to javafx.fxml,javafx.graphics;
    exports com.mws.prototype.ospf2;
}