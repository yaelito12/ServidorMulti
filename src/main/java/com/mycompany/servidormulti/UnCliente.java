package com.mycompany.servidormulti;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class UnCliente implements Runnable {
    private final DataOutputStream salida;
    private final DataInputStream entrada;
    private final Socket socket;
    private String nombreCliente;

    public UnCliente(Socket socket) throws IOException {
        this.socket = socket;
        this.salida = new DataOutputStream(socket.getOutputStream());
        this.entrada = new DataInputStream(socket.getInputStream());
        this.nombreCliente = null;
    }

    @Override
    public void run() {
        try {
            
            salida.writeUTF("=== BIENVENIDO AL CHAT ===");
            salida.writeUTF("Por favor, ingresa tu nombre:");
            
            
            boolean nombreValido = false;
            while (!nombreValido) {
                nombreCliente = entrada.readUTF().trim();
                
                if (nombreCliente.isEmpty()) {
                    salida.writeUTF("El nombre no puede estar vacío. Intenta de nuevo:");
                    continue;
                }
                
                if (nombreCliente.contains(" ") || nombreCliente.contains("@")) {
                    salida.writeUTF("El nombre no puede contener espacios ni '@'. Intenta de nuevo:");
                    continue;
                }
                
                if (ServidorMulti.nombreDisponible(nombreCliente)) {
                    ServidorMulti.registrarCliente(nombreCliente, this);
                    nombreValido = true;
                    salida.writeUTF("¡Bienvenido, " + nombreCliente + "! Ya puedes comenzar a chatear.");
                    System.out.println("Se conectó: " + nombreCliente);
                    
                    // Notificar a todos los demás que alguien nuevo se conectó
                    notificarATodos(nombreCliente + " se ha unido al chat.", this);
                } else {
                    salida.writeUTF("El nombre '" + nombreCliente + "' ya está en uso. Elige otro:");
                }
            }
            
            // Bucle principal de mensajes
            while (true) {
                String mensaje = entrada.readUTF();
                System.out.println("[" + nombreCliente + "]: " + mensaje);
               
                // Mensaje privado
                if (mensaje.startsWith("@")) {
                    String[] partes = mensaje.split(" ", 2);
                    if (partes.length >= 2) {
                        String destino = partes[0].substring(1);
                        String textoMensaje = partes[1];
                        UnCliente clienteDestino = ServidorMulti.clientes.get(destino);
                        
                        if (clienteDestino != null) {
                            clienteDestino.salida.writeUTF("[PRIVADO de " + nombreCliente + "]: " + textoMensaje);
                            salida.writeUTF("[Mensaje privado enviado a " + destino + "]: " + textoMensaje);
                        } else {
                            salida.writeUTF("[ERROR]: Usuario '" + destino + "' no encontrado.");
                        }
                    } else {
                        salida.writeUTF("[ERROR]: Formato incorrecto. Usa: @nombre mensaje");
                    }
                    continue;
                }
             
                // Mensaje público - enviar a TODOS EXCEPTO al remitente
                String mensajeCompleto = "[" + nombreCliente + "]: " + mensaje;
                for (UnCliente cliente : ServidorMulti.clientes.values()) {
                    if (cliente != this) {  // NO enviar al remitente
                        cliente.salida.writeUTF(mensajeCompleto);
                    }
                }
            }
        } catch (IOException ex) {
            System.out.println(nombreCliente + " se desconectó.");
        } finally {
            // Limpiar recursos y notificar desconexión
            if (nombreCliente != null) {
                ServidorMulti.clientes.remove(nombreCliente);
                notificarATodos(nombreCliente + " se ha desconectado.", this);
            }
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
 
    private void notificarATodos(String mensaje, UnCliente remitente) {
        for (UnCliente cliente : ServidorMulti.clientes.values()) {
            if (cliente != remitente) {
                try {
                    cliente.salida.writeUTF("[SISTEMA]: " + mensaje);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}