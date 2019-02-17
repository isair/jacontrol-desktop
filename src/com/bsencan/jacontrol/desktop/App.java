package com.bsencan.jacontrol.desktop;

import javax.swing.*;
import java.awt.*;

public class App {
    private JTabbedPane mainTabbedPane;
    private JPanel mainPanel;
    private JComboBox serverComboBox;
    private JTextField commandTextField;
    private JButton sendButton;
    private JTextArea responseTextArea;
    private JTextPane aboutTextPane;
    private JTextField addressTextField;
    private JTextField nameTextField;
    private JButton addServerButton;
    private JList serverList;
    private JButton deleteSelectedButton;
    private JTextField passwordTextField;

    public static void main(String[] args) {
        JFrame appFrame = new JFrame("JAControl Desktop");
        appFrame.setContentPane(new App().mainTabbedPane);
        appFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        appFrame.pack();
        appFrame.setSize(new Dimension(600, 400));
        appFrame.setMinimumSize(new Dimension(400, 300));
        appFrame.setVisible(true);
    }
}
