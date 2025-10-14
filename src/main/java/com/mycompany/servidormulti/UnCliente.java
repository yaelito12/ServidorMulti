package com.mycompany.servidormulti;

import java.io.*;
import java.net.Socket;

public class UnCliente implements Runnable {
    private final DataOutputStream salida;
    private final DataInputStream entrada;
    private final Socket socket;
    private String nombreCliente;
    private boolean autenticado;
    private int mensajesEnviados;

    public UnCliente(Socket socket) throws IOException {
        this.socket = socket;
        this.salida = new DataOutputStream(socket.getOutputStream());
        this.entrada = new DataInputStream(socket.getInputStream());
        this.nombreCliente = null;
        this.autenticado = false;
        this.mensajesEnviados = 0;
    }

    @Override
    public void run() {
        try {
            salida.writeUTF("=== BIENVENIDO AL CHAT ===");
            salida.writeUTF("Puedes enviar 3 mensajes de prueba antes de registrarte.");
            salida.writeUTF("Escribe 'registrar' o 'login' cuando quieras autenticarte.");
            salida.writeUTF("Escribe 'logout' para cerrar sesión.");
            salida.writeUTF("Escribe 'help' para ver todos los comandos disponibles.");
            
            nombreCliente = "invitado_" + System.currentTimeMillis();
            ServidorMulti.registrarCliente(nombreCliente, this);
            
            while (true) {
                String mensaje = entrada.readUTF();
                
                if (mensaje.equalsIgnoreCase("registrar")) {
                    registrarUsuario();
                    continue;
                } else if (mensaje.equalsIgnoreCase("login")) {
                    iniciarSesion();
                    continue;
                } else if (mensaje.equalsIgnoreCase("logout")) {
                    cerrarSesion();
                    continue;
                } else if (mensaje.equalsIgnoreCase("help")) {
                    mostrarAyuda();
                    continue;
                } else if (mensaje.toLowerCase().startsWith("bloquear ")) {
                    bloquearUsuarioCmd(mensaje.substring(9).trim());
                    continue;
                } else if (mensaje.toLowerCase().startsWith("desbloquear ")) {
                    desbloquearUsuarioCmd(mensaje.substring(12).trim());
                    continue;
                } else if (mensaje.equalsIgnoreCase("misBloqueados") || mensaje.equalsIgnoreCase("mis bloqueados")) {
                    mostrarMisBloqueados();
                    continue;
                }
                
                if (!autenticado && mensajesEnviados >= 3) {
                    salida.writeUTF("[SISTEMA]: Has alcanzado el límite de 3 mensajes.");
                    salida.writeUTF("[SISTEMA]: Escribe 'registrar' para crear una cuenta o 'login' para iniciar sesión.");
                    continue;
                }
                
                System.out.println("[" + nombreCliente + "]: " + mensaje);
                
                if (mensaje.startsWith("@")) {
                    manejarMensajePrivado(mensaje);
                    continue;
                }
                
                if (!autenticado) { 
                    mensajesEnviados++;
                    int restantes = 3 - mensajesEnviados;
                    if (restantes > 0) {
                        salida.writeUTF("[SISTEMA]: Mensaje enviado. Te quedan " + restantes + " mensajes.");
                    } else {
                        salida.writeUTF("[SISTEMA]: Has usado tus 3 mensajes gratuitos. Escribe 'registrar' o 'login' para continuar.");
                    }
                }
                
                String mensajeCompleto = "[" + nombreCliente + "]: " + mensaje;
                for (UnCliente cliente : ServidorMulti.clientes.values()) {
                    if (cliente != this) {
                        cliente.salida.writeUTF(mensajeCompleto);
                    }
                }
            }
        } catch (IOException ex) {
            System.out.println(nombreCliente + " se desconectó.");
        } finally {
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
    
    private void manejarMensajePrivado(String mensaje) throws IOException {
        String[] partes = mensaje.split(" ", 2);
        if (partes.length < 2) {
            salida.writeUTF("[ERROR]: Formato incorrecto. Usa: @nombre mensaje");
            return;
        }
        
        String destino = partes[0].substring(1);
        String textoMensaje = partes[1];
        
        if (!ServidorMulti.usuarios.containsKey(destino)) {
            salida.writeUTF("[ERROR]: Usuario '" + destino + "' no existe.");
            return;
        }
        
        if (!autenticado && mensajesEnviados >= 3) {
            salida.writeUTF("[ERROR]: Debes autenticarte para enviar mensajes privados.");
            return;
        }
        
        if (ServidorMulti.estasBloqueado(destino, nombreCliente)) {
            salida.writeUTF("[ERROR]: No puedes enviar mensajes a " + destino + " (bloqueado).");
            return;
        }
        
        UnCliente clienteDestino = ServidorMulti.clientes.get(destino);
        if (clienteDestino != null) {
            clienteDestino.salida.writeUTF("[PRIVADO de " + nombreCliente + "]: " + textoMensaje);
            salida.writeUTF("[Mensaje privado enviado a " + destino + "]: " + textoMensaje);
            if (!autenticado) mensajesEnviados++;
        } else {
            salida.writeUTF("[AVISO]: " + destino + " no está conectado en este momento.");
        }
    }
    
    private void bloquearUsuarioCmd(String usuarioABloquear) throws IOException {
        if (!autenticado) {
            salida.writeUTF("[ERROR]: Debes estar autenticado para bloquear usuarios.");
            return;
        }
        
        if (usuarioABloquear.isEmpty()) {
            salida.writeUTF("[ERROR]: Debes especificar un usuario. Usa: bloquear <nombre_usuario>");
            return;
        }
        
        if (usuarioABloquear.equals(nombreCliente)) {
            salida.writeUTF("[ERROR]: No puedes bloquearte a ti mismo.");
            return;
        }
        
        if (!ServidorMulti.usuarios.containsKey(usuarioABloquear)) {
            salida.writeUTF("[ERROR]: El usuario '" + usuarioABloquear + "' no existe.");
            return;
        }
        
        if (ServidorMulti.estasBloqueado(nombreCliente, usuarioABloquear)) {
            salida.writeUTF("[ERROR]: Ya tienes bloqueado a " + usuarioABloquear + ".");
            return;
        }
        
        if (ServidorMulti.bloquearUsuario(nombreCliente, usuarioABloquear)) {
            salida.writeUTF("[SISTEMA]: ¡Usuario '" + usuarioABloquear + "' bloqueado correctamente!");
            System.out.println(nombreCliente + " bloqueó a " + usuarioABloquear);
        } else {
            salida.writeUTF("[ERROR]: No se pudo bloquear al usuario. Intenta de nuevo.");
        }
    }
    
    private void desbloquearUsuarioCmd(String usuarioADesbloquear) throws IOException {
        if (!autenticado) {
            salida.writeUTF("[ERROR]: Debes estar autenticado para desbloquear usuarios.");
            return;
        }
        
        if (usuarioADesbloquear.isEmpty()) {
            salida.writeUTF("[ERROR]: Debes especificar un usuario. Usa: desbloquear <nombre_usuario>");
            return;
        }
        
        if (!ServidorMulti.usuarios.containsKey(usuarioADesbloquear)) {
            salida.writeUTF("[ERROR]: El usuario '" + usuarioADesbloquear + "' no existe.");
            return;
        }
        
        if (!ServidorMulti.estasBloqueado(nombreCliente, usuarioADesbloquear)) {
            salida.writeUTF("[ERROR]: No tienes bloqueado a " + usuarioADesbloquear + ".");
            return;
        }
        
        if (ServidorMulti.desbloquearUsuario(nombreCliente, usuarioADesbloquear)) {
            salida.writeUTF("[SISTEMA]: ¡Usuario '" + usuarioADesbloquear + "' desbloqueado correctamente!");
            System.out.println(nombreCliente + " desbloqueó a " + usuarioADesbloquear);
        } else {
            salida.writeUTF("[ERROR]: No se pudo desbloquear al usuario. Intenta de nuevo.");
        }
    }
    
    
    
    private void registrarUsuario() throws IOException {
        salida.writeUTF("[SISTEMA]: === REGISTRO ===");
        salida.writeUTF("[SISTEMA]: Ingresa tu nuevo nombre de usuario:");
        
        String nuevoNombre = entrada.readUTF().trim();
        
        if (nuevoNombre.isEmpty() || nuevoNombre.contains(" ") || nuevoNombre.contains("@")) {
            salida.writeUTF("[ERROR]: Nombre inválido. No puede contener espacios ni '@'. Intenta de nuevo escribiendo 'registrar'.");
            return;
        }
        
        if (!ServidorMulti.nombreDisponible(nuevoNombre) || ServidorMulti.usuarios.containsKey(nuevoNombre)) {
            salida.writeUTF("[ERROR]: El nombre '" + nuevoNombre + "' ya está en uso.");
            return;
        }
        
        salida.writeUTF("[SISTEMA]: Ingresa tu contraseña:");
        String password = entrada.readUTF().trim();
        
        if (password.isEmpty()) {
            salida.writeUTF("[ERROR]: La contraseña no puede estar vacía.");
            return;
        }
        
        ServidorMulti.registrarUsuario(nuevoNombre, password);
        
        ServidorMulti.clientes.remove(nombreCliente);
        String nombreAnterior = nombreCliente;
        nombreCliente = nuevoNombre;
        ServidorMulti.registrarCliente(nombreCliente, this);
        autenticado = true;
        mensajesEnviados = 0;
        
        salida.writeUTF("[SISTEMA]: ¡Registro exitoso! Ahora eres: " + nombreCliente);
        System.out.println(nombreAnterior + " se registró como: " + nombreCliente);
        notificarATodos(nombreCliente + " se ha unido al chat.", this);
    }
    
    private void iniciarSesion() throws IOException {
        salida.writeUTF("[SISTEMA]: === INICIO DE SESIÓN ===");
        salida.writeUTF("[SISTEMA]: Ingresa tu nombre de usuario:");
        
        String nombre = entrada.readUTF().trim();
        
        salida.writeUTF("[SISTEMA]: Ingresa tu contraseña:");
        String password = entrada.readUTF().trim();
        
        if (!ServidorMulti.autenticarUsuario(nombre, password)) {
            salida.writeUTF("[ERROR]: Usuario o contraseña incorrectos.");
            return;
        }
        
        if (!ServidorMulti.nombreDisponible(nombre)) {
            salida.writeUTF("[ERROR]: El usuario ya está conectado en otra sesión.");
            return;
        }
        
        ServidorMulti.clientes.remove(nombreCliente);
        String nombreAnterior = nombreCliente;
        nombreCliente = nombre;
        ServidorMulti.registrarCliente(nombreCliente, this);
        autenticado = true;
        mensajesEnviados = 0;
        
        salida.writeUTF("[SISTEMA]: ¡Inicio de sesión exitoso! Bienvenido de nuevo, " + nombreCliente);
        System.out.println(nombreAnterior + " inició sesión como: " + nombreCliente);
        notificarATodos(nombreCliente + " se ha unido al chat.", this);
    }
    
    private void cerrarSesion() throws IOException {
        if (!autenticado) {
            salida.writeUTF("[SISTEMA]: No has iniciado sesión.");
            return;
        }
        
        String nombreAnterior = nombreCliente;
        ServidorMulti.clientes.remove(nombreCliente);
        
        nombreCliente = "invitado_" + System.currentTimeMillis();
        ServidorMulti.registrarCliente(nombreCliente, this);
        
        autenticado = false;
        mensajesEnviados = 0;
        
        salida.writeUTF("[SISTEMA]: Has cerrado sesión. Ahora eres: " + nombreCliente);
        salida.writeUTF("[SISTEMA]: Tienes 3 mensajes gratuitos. Escribe 'login' para iniciar sesión nuevamente.");
        System.out.println(nombreAnterior + " cerró sesión y ahora es: " + nombreCliente);
        
        notificarATodos(nombreAnterior + " ha cerrado sesión.", this);
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