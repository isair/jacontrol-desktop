package com.bsencan.jacontrol.desktop;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static javax.swing.JOptionPane.showMessageDialog;

public class App {
    private final String rootDir = new JFileChooser().getFileSystemView().getDefaultDirectory().toString();
    private final File jacontrolDir = new File(rootDir + "/.jacontrol");
    private final File serverListFile = new File(jacontrolDir + "/servers.txt");

    // TODO: There should be a server model (remove tuple) and more than one lookup data structures.
    private HashMap<String, Tuple<String, String>> servers = new HashMap<>(); // address: (password, name)

    private JTabbedPane mainTabbedPane;
    private JPanel mainPanel;
    private JComboBox<String> serverComboBox;
    private JTextField commandTextField;
    private JButton sendButton;
    private JTextArea responseTextArea;
    private JTextPane aboutTextPane;
    private JTextField addressTextField;
    private JTextField nameTextField;
    private JButton addServerButton;
    private JList<String> serverList;
    private JButton deleteSelectedButton;
    private JTextField passwordTextField;

    private App() {
        loadServerListToUI();

        addServerButton.addActionListener(e ->
                onAddServerPress()
        );
        deleteSelectedButton.addActionListener(e ->
                onDeleteSelectedPress()
        );
        sendButton.addActionListener(e ->
                onSendPress()
        );
    }

    public static void main(String[] args) {
        JFrame appFrame = new JFrame("JAControl Desktop");
        appFrame.setContentPane(new App().mainTabbedPane);
        appFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        appFrame.pack();
        appFrame.setSize(new Dimension(600, 400));
        appFrame.setMinimumSize(new Dimension(400, 300));
        appFrame.setVisible(true);
    }

    //region JAServer

    private static boolean validateServer(String address, String rconPassword, String name) {
        Pattern p = Pattern.compile("^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5]):[0-9]+$");
        Matcher m = p.matcher(address);

        if (!m.find()) {
            return false;
        }

        if (rconPassword.length() <= 0) {
            return false;
        }

        return name.length() > 0;
    }

    private static String sendCommand(String command, String address, String password) throws IOException {
        String[] ipAndPort = address.split(":");
        InetAddress ip = InetAddress.getByName(ipAndPort[0]);
        int port = Integer.parseInt(ipAndPort[1]);

        byte[] bufferPrefix = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        byte[] bufferSuffix = ("rcon " + password + " " + command).getBytes(StandardCharsets.US_ASCII);
        byte[] buffer = new byte[bufferPrefix.length + bufferSuffix.length];

        System.arraycopy(bufferPrefix, 0, buffer, 0, bufferPrefix.length);
        System.arraycopy(bufferSuffix, 0, buffer, bufferPrefix.length, bufferSuffix.length);

        DatagramSocket socket = new DatagramSocket(port);
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, ip, port);

        socket.send(packet);

        byte[] responseBuffer = new byte[32768];
        boolean isCommandMultiResponse = command.equals("status");

        DatagramPacket response = new DatagramPacket(responseBuffer, responseBuffer.length);
        socket.setSoTimeout(5000);
        socket.receive(response);

        StringBuilder fullResponse = new StringBuilder(new String(responseBuffer, 0, response.getLength()));

        socket.setSoTimeout(1000);
        for (short i = 0; i < (isCommandMultiResponse ? 2 : 0); i++) {
            response = new DatagramPacket(responseBuffer, responseBuffer.length);
            try {
                socket.receive(response);
                fullResponse.append(new String(responseBuffer, 0, response.getLength()));
            } catch (SocketTimeoutException e) {
                i = 2;
            }
        }

        socket.close();

        return fullResponse.toString();
    }

    //endregion

    //region Data Persistence & UI Updates

    // TODO: They are a bit tangled together. Responsibilities should be separated better.

    private void loadServerListToUI() {
        if (!jacontrolDir.exists() && !jacontrolDir.mkdir()) {
            return;
        }

        servers = new HashMap<>();

        try {
            FileReader fr = new FileReader(serverListFile.getAbsoluteFile());
            BufferedReader br = new BufferedReader(fr);
            String line, address = null, password = null, name;
            while ((line = br.readLine()) != null && !line.isEmpty()) {
                if (address == null) {
                    address = line;
                } else if (password == null) {
                    password = line;
                } else {
                    name = line;
                    servers.put(address, new Tuple<>(password, name));
                    address = null;
                    password = null;
                }
            }
            br.close();
        } catch (IOException e) {
            // Do nothing.
        }

        refreshUIForServerListChange();
    }

    private void saveServer(String address, String rconPassword, String name) {
        if (!validateServer(address, rconPassword, name)) {
            showMessageDialog(null, "Please enter valid server details.");
            return;
        }

        if (servers.containsKey(address)) {
            showMessageDialog(null, "A server with that address already exists in your list.");
            return;
        }

        servers.put(address, new Tuple<>(rconPassword, name));
        refreshUIForServerListChange();

        if (writeServersToFileSystem()) {
            addressTextField.setText("");
            passwordTextField.setText("");
            nameTextField.setText("");
        } else {
            showMessageDialog(null, "Failed to save your server details. Please try again.");
        }
    }

    private boolean writeServersToFileSystem() {
        if (!jacontrolDir.exists() && !jacontrolDir.mkdir()) {
            return false;
        }

        try {
            FileWriter fw = new FileWriter(serverListFile.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            for (String address : servers.keySet()) {
                Tuple<String, String> passwordAndName = servers.get(address);
                bw.write(address + "\n");
                bw.write(passwordAndName.first + "\n");
                bw.write(passwordAndName.second + "\n");
            }
            bw.close();
        } catch (IOException e) {
            return false;
        }

        return true;
    }

    private void refreshUIForServerListChange() {
        DefaultListModel<String> serverListModel = new DefaultListModel<>();
        DefaultComboBoxModel<String> serverComboBoxModel = new DefaultComboBoxModel<>();

        serverComboBox.setModel(serverComboBoxModel);
        serverList.setModel(serverListModel);

        for (String address : servers.keySet()) {
            String name = servers.get(address).second;
            String element = name + " (" + address + ")";
            serverListModel.add(serverListModel.size(), element);
            serverComboBoxModel.addElement(element);
        }
    }

    //endregion

    //region UI Event Handlers

    private void onAddServerPress() {
        saveServer(addressTextField.getText().trim(), passwordTextField.getText().trim(), nameTextField.getText().trim());
    }

    private void onDeleteSelectedPress() {
        Object selectedValue = serverList.getSelectedValue();

        if (selectedValue == null) {
            return;
        }

        String valueToDelete = serverList.getSelectedValue();
        String keyToDelete = null;

        for (String address : servers.keySet()) {
            String name = servers.get(address).second;
            String valueToCheck = name + " (" + address + ")";
            if (valueToCheck.equals(valueToDelete)) {
                keyToDelete = address;
                break;
            }
        }

        if (keyToDelete != null) {
            servers.remove(keyToDelete);
            writeServersToFileSystem();
            refreshUIForServerListChange();
        }
    }

    private void onSendPress() {
        Object selectedItem = serverComboBox.getSelectedItem();

        if (selectedItem == null) {
            return;
        }

        String selectedValue = serverComboBox.getSelectedItem().toString();
        String selectedAddress = null;

        for (String address : servers.keySet()) {
            String name = servers.get(address).second;
            String valueToCheck = name + " (" + address + ")";
            if (valueToCheck.equals(selectedValue)) {
                selectedAddress = address;
                break;
            }
        }

        if (selectedAddress == null) {
            return;
        }

        String password = servers.get(selectedAddress).first;

        try {
            String response = sendCommand(commandTextField.getText().trim(), selectedAddress, password);
            responseTextArea.setText(response);
        } catch (Exception e) {
            responseTextArea.setText(e.toString());
        }
    }

    //endregion

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridBagLayout());
        mainTabbedPane = new JTabbedPane();
        GridBagConstraints gbc;
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(mainTabbedPane, gbc);
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridBagLayout());
        mainTabbedPane.addTab("Send Commands", panel1);
        serverComboBox = new JComboBox();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 5.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(serverComboBox, gbc);
        final JLabel label1 = new JLabel();
        label1.setText("Server");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(label1, gbc);
        final JLabel label2 = new JLabel();
        label2.setText("Command");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel1.add(label2, gbc);
        commandTextField = new JTextField();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 4.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(commandTextField, gbc);
        sendButton = new JButton();
        sendButton.setText("Send");
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(sendButton, gbc);
        final JScrollPane scrollPane1 = new JScrollPane();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 10.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel1.add(scrollPane1, gbc);
        responseTextArea = new JTextArea();
        responseTextArea.setLineWrap(true);
        responseTextArea.setText("Server response will be displayed here.");
        responseTextArea.setWrapStyleWord(true);
        scrollPane1.setViewportView(responseTextArea);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridBagLayout());
        mainTabbedPane.addTab("Manage Servers", panel2);
        final JLabel label3 = new JLabel();
        label3.setText("Server Address");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel2.add(label3, gbc);
        addressTextField = new JTextField();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 5.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel2.add(addressTextField, gbc);
        final JLabel label4 = new JLabel();
        label4.setText("Server Name");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel2.add(label4, gbc);
        nameTextField = new JTextField();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 5.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel2.add(nameTextField, gbc);
        addServerButton = new JButton();
        addServerButton.setText("Add Server");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel2.add(addServerButton, gbc);
        serverList = new JList();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 10.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel2.add(serverList, gbc);
        deleteSelectedButton = new JButton();
        deleteSelectedButton.setText("Delete Selected");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel2.add(deleteSelectedButton, gbc);
        final JLabel label5 = new JLabel();
        label5.setText("Rcon Password");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel2.add(label5, gbc);
        passwordTextField = new JTextField();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 5.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel2.add(passwordTextField, gbc);
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridBagLayout());
        mainTabbedPane.addTab("About", panel3);
        final JLabel label6 = new JLabel();
        label6.setText("Author: Baris Sencan <baris@ilyssa.software>");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(8, 8, 0, 0);
        panel3.add(label6, gbc);
        final JLabel label7 = new JLabel();
        label7.setText("Version: 0.2.0");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 8, 0, 0);
        panel3.add(label7, gbc);
        aboutTextPane = new JTextPane();
        aboutTextPane.setEditable(false);
        aboutTextPane.setText("This is a quickly thrown together application for sending rcon commands to Jedi Academy servers without going in game.\n\nIt has been developed as a temporary solution until my hosting service allows for rcon and smod commands to be sent via Discord private messages to my bot 'Yoda'. Despite this, small improvements can come in the future and it will also definitely be open sourced.\n\nIf you want to know more about my hosting service, send me a message on MBII forums or write an e-mail. Some of the top servers in the game already use it due to its many features over other hosting services. To get a sneak peek of these features, check out SF servers. Their names start with }{ in the server list at the moment.");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 8, 8, 8);
        panel3.add(aboutTextPane, gbc);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

}
