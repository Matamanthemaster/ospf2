package com.mws.prototype.ospf2;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class OspfUIController {
    @FXML private Label lblDispRID;
    @FXML private TextField txtRID;

    @FXML
    protected void initialize()
    {
        System.out.println(this + ".initialize()");
    }

    @FXML
    protected void TxtRIDKeyPressed(KeyEvent ev)
    {
        System.out.println(this + ".TxtRIDKeyPressed()");
        if (ev.getCode() == KeyCode.ENTER)
        {
            System.out.println("ENTER");
        }
    }
}
