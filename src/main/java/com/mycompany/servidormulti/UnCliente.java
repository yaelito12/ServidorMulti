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
                } else if (mensaje.equals("@") || mensaje.equalsIgnoreCase("privado")) {
                    mostrarUsuariosYEnviarMensaje();
                    continue;
                } else if (mensaje.equalsIgnoreCase("bloquear")) {
                    mostrarUsuariosYBloquear();
                    continue;
                } else if (mensaje.equalsIgnoreCase("desbloquear")) {
                    mostrarBloqueadosYDesbloquear();
                    continue;
                } else if (mensaje.equalsIgnoreCase("misBloqueados") || mensaje.equalsIgnoreCase("mis bloqueados")) {
                    mostrarMisBloqueados();
                    continue;
                } else if (mensaje.equalsIgnoreCase("gato") || mensaje.equalsIgnoreCase("jugar")) {
                    invitarAJugarGato();
                    continue;
                } else if (mensaje.equalsIgnoreCase("aceptar")) {
                    aceptarInvitacionGato();
                    continue;
                } else if (mensaje.equalsIgnoreCase("rechazar")) {
                    rechazarInvitacionGato();
                    continue;
                } else if (mensaje.equalsIgnoreCase("partidas")) {
                    mostrarPartidasActivas();
                    continue;
                } else if (mensaje.toLowerCase().startsWith("jugar ")) {
                    realizarMovimientoGato(mensaje);
                    continue;
                } else if (mensaje.equalsIgnoreCase("rendirse")) {
                    rendirseEnPartida();
                    continue;
                }
                
                if (!autenticado && mensajesEnviados >= 3) {
                    salida.writeUTF("[SISTEMA]: Has alcanzado el límite de 3 mensajes.");
                    salida.writeUTF("[SISTEMA]: Escribe 'registrar' para crear una cuenta o 'login' para iniciar sesión.");
                    continue;
                }
                
                System.out.println("[" + nombreCliente + "]: " + mensaje);
                
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
            manejarDesconexion();
        }
    }
    
    private void manejarDesconexion() {
        if (nombreCliente != null) {
            // Manejar partidas activas
            java.util.List<PartidaGato> partidas = ServidorMulti.obtenerPartidasDeJugador(nombreCliente);
            for (PartidaGato partida : partidas) {
                if (!partida.isTerminado()) {
                    partida.abandonar(nombreCliente);
                    String oponente = partida.getOponente(nombreCliente);
                    UnCliente clienteOponente = ServidorMulti.clientes.get(oponente);
                    
                    if (clienteOponente != null) {
                        try {
                            clienteOponente.salida.writeUTF("[GATO]: " + nombreCliente + " se desconectó. ¡Has ganado la partida!");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    ServidorMulti.finalizarPartida(partida.getJugador1(), partida.getJugador2());
                }
            }
            
            ServidorMulti.clientes.remove(nombreCliente);
            notificarATodos(nombreCliente + " se ha desconectado.", this);
        }
        
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void mostrarUsuariosYEnviarMensaje() throws IOException {
        StringBuilder usuarios = new StringBuilder();
        int contador = 0;
        for (String usuario : ServidorMulti.clientes.keySet()) {
            if (!usuario.equals(nombreCliente) && !usuario.startsWith("invitado_")) {
                if (contador > 0) usuarios.append(", ");
                usuarios.append(usuario);
                contador++;
            }
        }
        
        if (contador == 0) {
            salida.writeUTF("[SISTEMA]: No hay usuarios online.");
            return;
        }
        
        salida.writeUTF("[USUARIOS ONLINE]: " + usuarios.toString());
        salida.writeUTF("[SISTEMA]: Escribe: usuario mensaje");
        
        String input = entrada.readUTF().trim();
        String[] partes = input.split(" ", 2);
        
        if (partes.length < 2) {
            salida.writeUTF("[ERROR]: Formato incorrecto. Usa: usuario mensaje");
            return;
        }
        
        String destino = partes[0];
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
    
    private void mostrarUsuariosYBloquear() throws IOException {
        if (!autenticado) {
            salida.writeUTF("[ERROR]: Debes estar autenticado para bloquear usuarios.");
            return;
        }
        
        java.util.List<String> bloqueados = ServidorMulti.obtenerBloqueados(nombreCliente);
        StringBuilder usuarios = new StringBuilder();
        int contador = 0;
        
        for (String usuario : ServidorMulti.usuarios.keySet()) {
            if (!usuario.equals(nombreCliente) && !bloqueados.contains(usuario)) {
                if (contador > 0) usuarios.append(", ");
                String estado = ServidorMulti.clientes.containsKey(usuario) ? "[ON]" : "[OFF]";
                usuarios.append(usuario).append(estado);
                contador++;
            }
        }
        
        if (contador == 0) {
            salida.writeUTF("[SISTEMA]: No hay usuarios disponibles para bloquear.");
            return;
        }
        
        salida.writeUTF("[USUARIOS]: " + usuarios.toString());
        salida.writeUTF("[SISTEMA]: Escribe el nombre del usuario:");
        
        String usuarioABloquear = entrada.readUTF().trim();
        
        if (usuarioABloquear.isEmpty()) {
            salida.writeUTF("[SISTEMA]: Operación cancelada.");
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
    
    private void mostrarBloqueadosYDesbloquear() throws IOException {
        if (!autenticado) {
            salida.writeUTF("[ERROR]: Debes estar autenticado para desbloquear usuarios.");
            return;
        }
        
        java.util.List<String> bloqueados = ServidorMulti.obtenerBloqueados(nombreCliente);
        
        if (bloqueados.isEmpty()) {
            salida.writeUTF("[SISTEMA]: No tienes usuarios bloqueados.");
            return;
        }
        
        StringBuilder usuarios = new StringBuilder();
        for (int i = 0; i < bloqueados.size(); i++) {
            if (i > 0) usuarios.append(", ");
            String estado = ServidorMulti.clientes.containsKey(bloqueados.get(i)) ? "[ON]" : "[OFF]";
            usuarios.append(bloqueados.get(i)).append(estado);
        }
        
        salida.writeUTF("[BLOQUEADOS]: " + usuarios.toString());
        salida.writeUTF("[SISTEMA]: Escribe el nombre del usuario:");
        
        String usuarioADesbloquear = entrada.readUTF().trim();
        
        if (usuarioADesbloquear.isEmpty()) {
            salida.writeUTF("[SISTEMA]: Operación cancelada.");
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
    
    private void mostrarMisBloqueados() throws IOException {
        if (!autenticado) {
            salida.writeUTF("[ERROR]: Debes estar autenticado para ver tu lista de bloqueados.");
            return;
        }
        
        java.util.List<String> bloqueados = ServidorMulti.obtenerBloqueados(nombreCliente);
        
        if (bloqueados.isEmpty()) {
            salida.writeUTF("[SISTEMA]: No tienes usuarios bloqueados.");
        } else {
            salida.writeUTF("[SISTEMA]: === USUARIOS BLOQUEADOS ===");
            for (int i = 0; i < bloqueados.size(); i++) {
                salida.writeUTF((i + 1) + ". " + bloqueados.get(i));
            }
            salida.writeUTF("[SISTEMA]: Total: " + bloqueados.size() + " usuario(s) bloqueado(s)");
        }
    }
    
    private void invitarAJugarGato() throws IOException {
        if (!autenticado) {
            salida.writeUTF("[ERROR]: Debes estar autenticado para jugar.");
            return;
        }
        
        StringBuilder usuarios = new StringBuilder();
        int contador = 0;
        
        for (String usuario : ServidorMulti.clientes.keySet()) {
            if (!usuario.equals(nombreCliente) && !usuario.startsWith("invitado_")) {
                if (contador > 0) usuarios.append(", ");
                usuarios.append(usuario);
                contador++;
            }
        }
        
        if (contador == 0) {
            salida.writeUTF("[SISTEMA]: No hay usuarios disponibles para jugar.");
            return;
        }
        
        salida.writeUTF("[USUARIOS ONLINE]: " + usuarios.toString());
        salida.writeUTF("[SISTEMA]: Escribe el nombre del usuario:");
        
        String invitado = entrada.readUTF().trim();
        
        if (invitado.isEmpty()) {
            salida.writeUTF("[SISTEMA]: Operación cancelada.");
            return;
        }
        
        if (invitado.equals(nombreCliente)) {
            salida.writeUTF("[ERROR]: No puedes jugar contigo mismo.");
            return;
        }
        
        if (!ServidorMulti.clientes.containsKey(invitado)) {
            salida.writeUTF("[ERROR]: El usuario no está conectado.");
            return;
        }
        
        if (ServidorMulti.obtenerPartida(nombreCliente, invitado) != null) {
            salida.writeUTF("[ERROR]: Ya tienes una partida activa con " + invitado + ".");
            return;
        }
        
        if (!ServidorMulti.enviarInvitacionGato(nombreCliente, invitado)) {
            salida.writeUTF("[ERROR]: " + invitado + " ya tiene una invitación pendiente.");
            return;
        }
        
        UnCliente clienteInvitado = ServidorMulti.clientes.get(invitado);
        if (clienteInvitado != null) {
            clienteInvitado.salida.writeUTF("[GATO]: " + nombreCliente + " te invita a jugar. Escribe 'aceptar' o 'rechazar'.");
            salida.writeUTF("[SISTEMA]: Invitación enviada a " + invitado + ".");
            System.out.println(nombreCliente + " invitó a jugar a " + invitado);
        }
    }
    
    
    private void mostrarAyuda() throws IOException {
        salida.writeUTF("=== COMANDOS DISPONIBLES ===");
        salida.writeUTF("registrar - Crear una nueva cuenta");
        salida.writeUTF("login - Iniciar sesión");
        salida.writeUTF("logout - Cerrar sesión");
        salida.writeUTF("@ o privado - Enviar mensaje privado (muestra usuarios)");
        salida.writeUTF("bloquear - Bloquear usuario (muestra lista)");
        salida.writeUTF("desbloquear - Desbloquear usuario (muestra lista)");
        salida.writeUTF("misBloqueados - Ver lista de usuarios bloqueados");
        salida.writeUTF("--- JUEGO DEL GATO ---");
        salida.writeUTF("gato o jugar - Invitar a alguien a jugar");
        salida.writeUTF("aceptar - Aceptar invitación de juego");
        salida.writeUTF("rechazar - Rechazar invitación de juego");
        salida.writeUTF("partidas - Ver tus partidas activas");
        salida.writeUTF("jugar fila columna - Realizar movimiento (ej: jugar 1 2)");
        salida.writeUTF("rendirse - Abandonar partida actual");
        salida.writeUTF("help - Mostrar esta ayuda");
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