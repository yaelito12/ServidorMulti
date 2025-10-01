package com.mycompany.servidorMulti;

import com.mycompany.servidorMulti.ServidorMulti;
import java.io.*;
import java.net.Socket;
import java.util.Map;

public class UnCliente implements Runnable {

    private DataOutputStream salida;
    private DataInputStream entrada;
    private Socket socket;

    public UnCliente(Socket s) throws IOException {
        this.socket = s;
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
    }

    @Override
    public void run() {
        try {
            while (true) {
                // Lee el mensaje del cliente
                String mensaje = entrada.readUTF();

                System.out.println("Mensaje recibido: " + mensaje);

                // Reenvía el mensaje a todos los clientes conectados
                for (Map.Entry<String, UnCliente> entry : ServidorMulti.clientes.entrySet()) {
                    UnCliente cliente = entry.getValue();
                    cliente.salida.writeUTF(mensaje);
                }
            }
        } catch (IOException ex) {
            System.out.println("Cliente desconectado: " + socket);
            // Elimina el cliente de la lista global
            ServidorMulti.clientes.values().remove(this);
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}