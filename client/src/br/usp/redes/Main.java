package br.usp.redes;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A simple Swing-based client for the chat server. Graphically it is a frame with a text field for entering messages
 * and a textarea to see the whole dialog.
 * <p>
 * The client follows the Chat Protocol which is as follows. When the server sends "SUBMITNAME" the client replies with
 * the desired screen name. The server will keep sending "SUBMITNAME" requests as long as the client submits screen
 * names that are already in use. When the server sends a line beginning with "NAMEACCEPTED" the client is now allowed
 * to start sending the server arbitrary strings to be broadcast to all chatters connected to the server. When the
 * server sends a line beginning with "MESSAGE " then all characters following this string should be displayed in its
 * message area.
 * <p>
 * http://cs.lmu.edu/~ray/notes/javanetexamples/
 */
public class Main {

    private static List<Contact> onlineContacts;

    private static ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();

    public static void main(String[] args) throws IOException, BadLocationException {
        Main client = new Main();
        client.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        client.frame.setVisible(true);
        try {
            client.run();
        } finally {
            ses.shutdown();
        }
    }

    BufferedReader in;
    PrintWriter out;
    public JFrame frame = new JFrame("Melhor cliente de chat dos anos 90");
    JTextField textField = new JTextField(50);
    JTextPane messageArea = new JTextPane();
    StyledDocument doc;
    JScrollPane scrollPane;
    JScrollBar scrollPaneBar;
    UIDefaults defs = UIManager.getDefaults();

    Style publicMessageStyle;
    Style privateMessageStyle;
    Style notificationStyle;

    Style helpStyle;

    /**
     * Constructs the client by laying out the GUI and registering a listener with the textfield so that pressing Return
     * in the listener sends the textfield contents to the server. Note however that the textfield is initially NOT
     * editable, and only becomes editable AFTER the client receives the NAMEACCEPTED message from the server.
     */
    public Main() {

        // Define estilos para textos
        defs.put("TextPane.background", new ColorUIResource(Color.white));
        doc = messageArea.getStyledDocument();
        publicMessageStyle = messageArea.addStyle("publicMessage", null);
        privateMessageStyle = messageArea.addStyle("privateMessage", null);
        notificationStyle = messageArea.addStyle("notification", null);
        helpStyle = messageArea.addStyle("help", null);
        StyleConstants.setBold(publicMessageStyle, true);
        StyleConstants.setForeground(privateMessageStyle, Color.black);
        StyleConstants.setBold(privateMessageStyle, true);
        StyleConstants.setForeground(privateMessageStyle, Color.blue);
        StyleConstants.setForeground(notificationStyle, Color.red);
        StyleConstants.setForeground(helpStyle, Color.gray);

        // Define estilo da janela
        textField.setEditable(false);
        messageArea.setEditable(false);
        frame.getContentPane().add(textField, "North");
        scrollPane = new JScrollPane(messageArea);
        scrollPaneBar = scrollPane.getVerticalScrollBar();
        frame.getContentPane().add(scrollPane, "Center");
        frame.setSize(500, 400);

        // Adiciona listener para enviar texto na linha de entrada
        textField.addActionListener(new ActionListener() {
            /**
             * Responds to pressing the enter key in the textfield by sending the contents of the text field to the
             * server. Then clear the text area in preparation for the next message.
             */
            public void actionPerformed(ActionEvent e) {
                String command = textField.getText();
                if (command.equals("HELP")) {
                    try {
                        printHelp();
                    } catch (BadLocationException ignore) {
                    }
                    scrollPaneBar.setValue(scrollPaneBar.getMaximum());
                } else {
                    out.println(command);
                }
                textField.setText("");
            }
        });
    }

    /**
     * Conecta ao servidor e entra no loop de processamento
     */
    private void run() throws IOException, BadLocationException {

        // Faz conexao e inicia streams de entrada e saida
        String serverAddress = getServerAddress();
        Socket socket = new Socket(serverAddress, 9001);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        // Processa todas as mensagens do servidor, de acordo com o protocolo
        while (true) {
            String line = in.readLine();

            if (line.startsWith("WHORU")) {
                out.println(getName());
                continue;

            } else if (line.startsWith("CLIST")) {
                final String contactsMsg = line.split(" ")[1];
                onlineContacts = MessageParser.parseContactsFromMessage(contactsMsg);

                ses.scheduleAtFixedRate(() -> {
                    out.println("KEEPA");
                }, 0, 1, TimeUnit.SECONDS);

                textField.setEditable(true);
                printHelp();

            } else if (line.startsWith("CONAT")) {
                doc.insertString(doc.getLength(), "Lista de clientes: " + line.substring(5) + "\n", notificationStyle);

            } else if (line.startsWith("CONIN")) {
                doc.insertString(doc.getLength(), "Lista de contatos: " + line.substring(6) + "\n", notificationStyle);

            } else if (line.startsWith("KEPTA")) {
                doc.insertString(doc.getLength(), "Lista de IPs: " + line.substring(6) + "\n", notificationStyle);

            } else if (line.startsWith("MESSAGE")) {
                if (line.length() > 8 && line.charAt(8) == '*') {
                    doc.insertString(doc.getLength(), line.substring(8) + "\n", privateMessageStyle);
                } else {
                    doc.insertString(doc.getLength(), line.substring(8) + "\n", publicMessageStyle);
                }

            }
            scrollPaneBar.setValue(scrollPaneBar.getMaximum());

        }
    }

    /**
     * Mostra uma caixa de di�logo perguntando endere�o do servidor
     */
    private String getServerAddress() {
        return JOptionPane.showInputDialog(frame, "Endereco IP do servidor:", "Bem vindo!",
                JOptionPane.QUESTION_MESSAGE);
    }

    /**
     * Mostra uma caixa de di�logo perguntando nome de usuario
     */
    private String getName() {
        return JOptionPane.showInputDialog(frame, "Escolha um nome de usuario:", "Nome de Usuario",
                JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * Mostra uma caixa de di�logo perguntando contato do usuario
     */
    private String getContact() {
        return JOptionPane.showInputDialog(frame, "Email de contato:", "Email", JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * Mostra conte�do de ajuda com comandos dispon�veis para o usu�rio
     */
    private void printHelp() throws BadLocationException {
        doc.insertString(doc.getLength(), "= = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =\n", helpStyle);
        doc.insertString(doc.getLength(), "Comandos dispon�veis:\n", helpStyle);
        doc.insertString(doc.getLength(), "\n", helpStyle);
        doc.insertString(doc.getLength(), "GETNAMES: Lista nomes de usu�rios conectados\n", helpStyle);
        doc.insertString(doc.getLength(), "GETCONTACTS: Lista contatos de usu�rios conectados\n", helpStyle);
        doc.insertString(doc.getLength(), "GETIPS: Lista IPs de usu�rios conectados\n", helpStyle);
        doc.insertString(doc.getLength(), "Mensagem p�blica/broadcast: Simplesmente digite-a\n", helpStyle);
        doc.insertString(doc.getLength(), "Mensagem privada: Inicie com o nome do usu�rio \"@user msg\"\n", helpStyle);
        doc.insertString(doc.getLength(), "HELP: Mostra essa mensagem\n", helpStyle);
        doc.insertString(doc.getLength(), "= = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =\n", helpStyle);
    }
}
