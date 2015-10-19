package br.usp.redes;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * Adaptado de: http://cs.lmu.edu/~ray/notes/javanetexamples/
 */
public class Main {

    //Lista dos contatos online
    private static List<Contact> onlineContacts;

    //Nome desse cliente
    private static String clientName;
    //IP usado para escutar conexao com outros clientes
    private static String clientMessagesIp;
    //Porta usada para escutar conexao com outros clientes
    private static String clientMessagesPort;

    static JFrame frame = new JFrame("Melhor cliente de chat dos anos 90");

    static JPanel controlsPanel = new JPanel();

    static JLabel title = new JLabel("");
    static JTextPane messagesArea = new JTextPane();
    static JTextField inputField = new JTextField(25);

    static JComboBox<Contact> contactListSelection;

    static StyledDocument doc;

    static JScrollPane scrollPane;
    static JScrollBar scrollPaneBar;
    static UIDefaults defs = UIManager.getDefaults();
    static Style messagesStyle;

    static Style notificationStyle, helpStyle;


    public static void main(String[] args) throws IOException, BadLocationException {
        defineInterface();

        //Cria o socket que escuta conexoes de outros contatos
        ServerSocket listener = new ServerSocket(0);
        clientMessagesIp = listener.getInetAddress().getHostAddress();
        clientMessagesPort = String.valueOf(listener.getLocalPort());
        System.out.println("Cliente ira escutar outros clientes em " + clientMessagesIp + ":" + clientMessagesPort);

        //Cria o socket para conexao com o servidor
        ServerHandler serverHandler = new ServerHandler();
        serverHandler.start();

        try {
            System.out.println("Cliente escutando outros clientes...");
            while (true) {
                new ContactHandler(listener.accept()).start();
            }
        } finally {
            listener.close();
        }
    }

    private static void defineInterface() {
        // Define estilos para textos
        defs.put("TextPane.background", new ColorUIResource(Color.white));
        doc = messagesArea.getStyledDocument();
        messagesStyle = messagesArea.addStyle("publicMessage", null);
        notificationStyle = messagesArea.addStyle("notification", null);
        helpStyle = messagesArea.addStyle("help", null);
        StyleConstants.setBold(messagesStyle, true);
        StyleConstants.setForeground(notificationStyle, Color.red);
        StyleConstants.setForeground(helpStyle, Color.gray);

        // Define janela
        frame.getContentPane().add(title, BorderLayout.NORTH);
        messagesArea.setEditable(false);
        scrollPane = new JScrollPane(messagesArea);
        scrollPaneBar = scrollPane.getVerticalScrollBar();
        frame.getContentPane().add(scrollPane, BorderLayout.CENTER);

        contactListSelection = new JComboBox<>();
        contactListSelection.setSize(20, 80);
        inputField.setEditable(false);
        controlsPanel.add(contactListSelection, BorderLayout.EAST);
        controlsPanel.add(inputField, BorderLayout.WEST);
        frame.getContentPane().add(controlsPanel, BorderLayout.SOUTH);

        frame.setSize(500, 400);

        // Adiciona listener para enviar texto na linha de entrada
        inputField.addActionListener(e -> {
            String command = inputField.getText();
            Contact recipient = (Contact) contactListSelection.getSelectedItem();

            try {
                Socket socket = new Socket(recipient.getIp(), Integer.valueOf(recipient.getPort()));
                //Fluxo que envia mensagens ao contato
                PrintWriter contactOut = new PrintWriter(socket.getOutputStream(), true);
                contactOut.println(clientName + ": " + command);
                doc.insertString(doc.getLength(), clientName + ": " + command + "\n", notificationStyle);
            } catch (IOException | BadLocationException e1) {
                e1.printStackTrace();
            }

            scrollPaneBar.setValue(scrollPaneBar.getMaximum());
            inputField.setText("");
        });

        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private static void updateContactActive(String contactData) throws BadLocationException {
        final String contactName = contactData.split("/")[0];
        Contact contact = MessageParser.parseContactsFromMessage(contactData).get(0);
        onlineContacts.add(contact);
        contactListSelection.addItem(contact);
        inputField.setEditable(true);

        doc.insertString(doc.getLength(), contactName + " esta online.\n", notificationStyle);
    }

    private static void updateContactInactive(String contactName) throws BadLocationException {
        Contact toRemove = new Contact();
        toRemove.setName(contactName);
        contactListSelection.removeItem(toRemove);
        onlineContacts.remove(toRemove);
        if (onlineContacts.size() == 0) {
            inputField.setEditable(false);
        }

        doc.insertString(doc.getLength(), contactName + " esta offline.\n", notificationStyle);
    }

    /**
     * Mostra uma caixa de dialogo perguntando endereco do servidor
     */
    private static String getServerAddress() {
        return JOptionPane.showInputDialog(frame, "Endereco IP do servidor:", "Bem vindo!",
                JOptionPane.QUESTION_MESSAGE);
    }

    /**
     * Mostra uma caixa de dialogo perguntando nome de usuario
     */
    private static String getClientName() {
        return JOptionPane.showInputDialog(frame, "Digite o nome de usuario:", "Nome de Usuario",
                JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * Mostra conteudo de ajuda com comandos disponiveis para o usuario
     */
    private static void printHelp() throws BadLocationException {
        doc.insertString(doc.getLength(), "= = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =\n", helpStyle);
        doc.insertString(doc.getLength(), "Selecione um contato, digite a mensagem e aperte Enter.\n", helpStyle);
        doc.insertString(doc.getLength(), "= = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =\n", helpStyle);
    }

    //Classe que gerencia a conexao com o servidor
    private static class ServerHandler extends Thread {

        //Objeto para ficar executando o Keep Alive
        private static ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();

        //Fluxo que recebe mensagens do servidor
        static BufferedReader serverIn;

        //Fluxo que envia mensagens ao servidor
        static PrintWriter serverOut;

        /**
         * Conecta ao servidor e entra no loop de processamento
         */
        public void run() {

            try {
                String serverAddress = getServerAddress();
                if (serverAddress == null) {
                    frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
                    return;
                }
                Socket socket = new Socket(serverAddress, 9001);
                serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                serverOut = new PrintWriter(socket.getOutputStream(), true);

                // Processa todas as mensagens do servidor, de acordo com o protocolo
                while (true) {
                    String line = serverIn.readLine();
                    System.out.println("Received:" + line);

                    if (line == null || line.startsWith("null")) {
                        //Servidor nao respondendo KEPTA.
                        doc.insertString(doc.getLength(), "ERRO: Servidor desconectou. Favor reiniciar o programa\n",
                                notificationStyle);
                        contactListSelection.removeAll();
                        inputField.setEditable(false);
                        return;

                    } else if (line.startsWith("WHORU")) {
                        //Servidor perguntando Who Are You
                        clientName = getClientName();
                        if (clientName == null) {
                            frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
                            return;
                        }
                        serverOut.println(clientName + "/" + clientMessagesIp + ":" + clientMessagesPort);

                    } else if (line.startsWith("CLIST")) {
                        //Conexao com servidor bem sucedida.
                        //Inicializa interface cliente.
                        title.setText("Conectado como " + clientName + " no servidor " + serverAddress);
                        final String[] contactsMsg = line.split(" ");
                        if (contactsMsg.length > 1) {
                            //Se tiver recebido pelo menos algum contato
                            onlineContacts = MessageParser.parseContactsFromMessage(contactsMsg[1]);
                            for (Contact contact : onlineContacts) {
                                contactListSelection.addItem(contact);
                            }
                            doc.insertString(doc.getLength(), "Contatos online:" + onlineContacts + "\n", notificationStyle);
                        } else {
                            //Se nao tinver recebido nenhum contato online
                            onlineContacts = new ArrayList<>();
                            doc.insertString(doc.getLength(), "Nenhum contato online.\n", notificationStyle);
                        }

                        //Comeca a enviar mensagens de Keep Alive
                        ses.scheduleAtFixedRate(() -> serverOut.println("KEEPA"), 0, 1, TimeUnit.SECONDS);

                        inputField.setEditable(true);
                        printHelp();

                    } else if (line.startsWith("CONAT")) {
                        updateContactActive(line.split(" ")[1]);

                    } else if (line.startsWith("CONIN")) {
                        updateContactInactive(line.split(" ")[1]);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                //Finaliza o servico que envia Keep Alive ao servidor
                ses.shutdown();
            }

        }

    }

    //Classe que recebe conexoes de outros clientes/contatos
    private static class ContactHandler extends Thread {

        private Socket socket;

        //Fluxo de entrada de dados. Recebe do cliente
        private BufferedReader in;

        public ContactHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                // Create character streams for the socket.
                while (true) {
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    final String message = in.readLine();

                    if (message != null) {
                        doc.insertString(doc.getLength(), message + "\n", messagesStyle);
                        break;
                    }
                }

            } catch (IOException | BadLocationException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }

        }

    }
}
