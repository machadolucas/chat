package br.usp.redes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Protocolo de comunicacao:
 * <p>
 * Servidor ao receber conexao, pergunta "WHURO" (Who are you) ate o cliente entrar com um nome valido e que nao
 * esta em uso. Quando o cliente entra com um nome valido (clientName), o servidor notifica os contatos do cliente
 * conectados com "CONAT clientName/ip:porta" (Connection Active), e responde ao cliente
 * "CLIST contato1/ip1:porta1;contato2/ip2:porta2" (Contact List).
 * <p>
 * O servidor fica entao continuamente escutando por mensagens "KEEPA" (Keep Alive) do cliente, e respondendo-as com
 * "KEPTA" (Kept Alive). Caso o cliente fique sem mandar um numero MISSED_KEEP_ALIVE_LIMIT de mensagens KEEPA, que sao
 * esperadas a um intervalo de KEEP_ALIVE_INTERVAL, o servidor desconecta o cliente, e notifica todos seus contatos
 * conectados com a mensagem "CONIN clientName" (Connection Inactive).
 * <p>
 * Codigo adaptado de:
 * http://cs.lmu.edu/~ray/notes/javanetexamples/
 */
public class Main {

    /**
     * Porta na qual o servidor fica escutando.
     */
    private static final int PORT = 9001;

    private static final int KEEP_ALIVE_INTERVAL = 1000;

    private static final int MISSED_KEEP_ALIVE_LIMIT = 5;

    /**
     * Executa o servidor, escutando na porta e criando uma thread Handler para cada conexao
     */
    public static void main(String[] args) throws IOException {

        clientContacts = FilesInterpreter.getClientsAndContacts();

        ServerSocket listener = new ServerSocket(PORT);
        System.out.println("Servidor executando e esperando por clientes...");
        try {
            while (true) {
                new Handler(listener.accept()).start();
            }
        } finally {
            listener.close();
        }
    }

    /**
     * Conjunto de clientes conectados ao servidor no momento
     */
    private static final List<Client> clients = new ArrayList<>();

    /**
     * Conjunto de buffers de saida para os clientes conectados. Usa os mesmos indices que a lista clients.
     */
    private static List<PrintWriter> writers = new ArrayList<>();

    /**
     * Mapa com uma lista de nomes de contatos de cada cliente. Eh inicializada a partir do arquivo clients.txt ao
     * subir o servidor. Tem o formato: "cliente1=[contato1,contato2]"
     */
    private static HashMap<String, List<String>> clientContacts;

    /**
     * Busca um cliente conectado pelo nome e retorna o indice se existente, ou -1 caso contrario
     */
    static int clientNameIndex(String name) {
        for (int i = 0; i < clients.size(); i++) {
            Client c = clients.get(i);
            if (c.getName().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Classe que eh instanciada para cada conexao com cada cliente e gerencia a comunicacao com ele.
     */
    private static class Handler extends Thread {

        private String clientName;
        private List<String> contacts;

        private Socket socket;

        //Fluxo de entrada de dados. Recebe do cliente
        private BufferedReader in;

        //Fluxo de saida de dados. Envia para o cliente
        private PrintWriter out;

        public Handler(Socket socket) {
            this.socket = socket;
        }

        /**
         * Services this thread's client by repeatedly requesting a screen clientName until a unique one has been submitted,
         * then acknowledges the clientName and registers the output stream for the client in a global set, then repeatedly
         * gets inputs and broadcasts them.
         */
        public void run() {
            try {

                // Create character streams for the socket.
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Request a clientName from this client. Keep requesting until
                // a clientName is submitted that is not already used. Note that
                // checking for the existence of a clientName and adding the clientName
                // must be done while locking the set of names.
                while (true) {
                    out.println("WHORU");
                    clientName = in.readLine();
                    if (clientName == null) {
                        return;
                    }
                    synchronized (clients) {
                        if (clientNameIndex(clientName) != -1 && clientContacts.containsKey(clientName)) {
                            out.println("Denied. Not a valid client.");
                        } else {
                            Client c = new Client();
                            c.setName(clientName);
                            c.setIp(socket.getLocalAddress().toString());
                            c.setPort(String.valueOf(socket.getLocalPort()));
                            clients.add(c);
                            break;
                        }
                    }
                }
                //Cliente conectado com sucesso.

                //Pega os contatos do cliente que estavam no arquivo clients.txt
                contacts = clientContacts.get(clientName);

                // Avisa aos contatos conectados do cliente que ele esta conectando.
                System.out.println("(@" + clientName + ") conectou-se.");
                final StringBuilder clientNameIp = new StringBuilder();
                clientNameIp.append(clientName)
                        .append("/").append(socket.getLocalAddress().toString())
                        .append(":").append(String.valueOf(socket.getLocalPort()));
                for (int i = 0; i < writers.size(); i++) {
                    if (contacts.contains(clients.get(i).getName())) {
                        final PrintWriter writer = writers.get(i);
                        writer.println("CONAT " + clientNameIp.toString());
                    }
                }
                writers.add(out);

                //Envia ao cliente a lista de seus contatos que estao conectados
                final StringBuilder onlineContactList = new StringBuilder();
                for (int i = 0; i < contacts.size(); i++) {
                    String contact = contacts.get(i);
                    int clientNameIndex;
                    synchronized (clients) {
                        clientNameIndex = clientNameIndex(contact);
                    }
                    if (clientNameIndex != -1) {
                        onlineContactList.append(clients.get(clientNameIndex).getName())
                                .append("/").append(clients.get(clientNameIndex).getIp())
                                .append(":").append(clients.get(clientNameIndex).getPort());
                        if (i < contacts.size() - 1) {
                            onlineContactList.append(";");
                        }
                    }
                }
                out.println("CLIST " + onlineContactList.toString());

                // Depois do protocolo inicial, aceita mensagens de Keep Alive (KEEPA) dos clientes, e responde KEPTA.
                // Caso um cliente fique sem mandar MISSED_KEEP_ALIVE_LIMIT por KEEP_ALIVE_INTERVAL, desconecta.
                long lastKeepAlive = System.currentTimeMillis();
                while ((System.currentTimeMillis() - lastKeepAlive) < KEEP_ALIVE_INTERVAL * MISSED_KEEP_ALIVE_LIMIT) {
                    String input = in.readLine();
                    if (input == null) {
                        return;
                    }
                    // Keep Alive
                    if (input.startsWith("KEEPA")) {
                        lastKeepAlive = System.currentTimeMillis();
                        out.println("KEPTA");
                        continue;
                    }
                }
            } catch (IOException e) {
                System.out.println(e);
            } finally {
                // Cliente esta saindo. Precisa remover ele das listas
                if (clientName != null) {
                    int clientIndex = clientNameIndex(clientName);
                    clients.remove(clientIndex);
                }
                if (out != null) {
                    writers.remove(out);
                } else {
                    writers.removeAll(Collections.singleton(null));
                }
                // Avisa que o cliente esta desconectando aos seus contatos
                System.out.println("(@" + clientName + ") desconectou.");
                for (int i = 0; i < clients.size(); i++) {
                    if (contacts.contains(clients.get(i).getName())) {
                        PrintWriter writer = writers.get(i);
                        writer.println("CONIN " + clientName);
                    }
                }
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
