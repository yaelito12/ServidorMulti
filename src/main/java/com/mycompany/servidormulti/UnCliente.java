package com.mycompany.servidormulti;

import java.io.*;
import java.net.Socket;
import java.util.Optional;

public class UnCliente implements Runnable {
    private static final int MENSAJES_GRATUITOS = 3;
    private static final String PREFIJO_INVITADO = "invitado_";
    
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
            inicializarCliente();
            
            while (true) {
                String mensaje = entrada.readUTF();
                
                ComandoHandler handler = obtenerHandlerComando(mensaje);
                if (handler.ejecutar()) continue;
                
                if (!verificarLimiteMensajes()) continue;
                
                procesarMensajeRegular(mensaje);
            }
        } catch (IOException ex) {
            System.out.println(nombreCliente + " se desconectó.");
        } finally {
            manejarDesconexion();
        }
    }
    
    
    private ComandoHandler obtenerHandlerComando(String mensaje) {
        String cmd = mensaje.toLowerCase();
        
        if (cmd.equals("registrar")) return () -> { registrarUsuario(); return true; };
        if (cmd.equals("login")) return () -> { iniciarSesion(); return true; };
        if (cmd.equals("logout")) return () -> { cerrarSesion(); return true; };
        if (cmd.equals("help")) return () -> { mostrarAyuda(); return true; };
        if (cmd.equals("@") || cmd.equals("privado")) return () -> { mostrarUsuariosYEnviarMensaje(); return true; };
        if (cmd.equals("bloquear")) return () -> { mostrarUsuariosYBloquear(); return true; };
        if (cmd.equals("desbloquear")) return () -> { mostrarBloqueadosYDesbloquear(); return true; };
        if (cmd.equals("misbloqueados") || cmd.equals("mis bloqueados")) return () -> { mostrarMisBloqueados(); return true; };
        if (cmd.equals("gato") || cmd.equals("jugar")) return () -> { invitarAJugarGato(); return true; };
        if (cmd.equals("aceptar")) return () -> { aceptarInvitacionGato(); return true; };
        if (cmd.equals("rechazar")) return () -> { rechazarInvitacionGato(); return true; };
        if (cmd.equals("partidas")) return () -> { mostrarPartidasActivas(); return true; };
        if (cmd.equals("rendirse")) return () -> { rendirseEnPartida(); return true; };
        if (esMovimientoGato(mensaje)) return () -> { realizarMovimientoGato(esFormatoSimple(mensaje) ? "jugar " + mensaje : mensaje); return true; };
        
        return () -> false;
    }
    
    private boolean esMovimientoGato(String mensaje) {
        return mensaje.toLowerCase().startsWith("jugar ") || mensaje.matches("^[1-3]\\s+[1-3]$");
    }
    
    private boolean esFormatoSimple(String mensaje) {
        return mensaje.matches("^[1-3]\\s+[1-3]$");
    }
    
    private boolean verificarLimiteMensajes() throws IOException {
        boolean limiteAlcanzado = !autenticado && mensajesEnviados >= MENSAJES_GRATUITOS;
        if (limiteAlcanzado) {
            salida.writeUTF("[SISTEMA]: Has alcanzado el límite de 3 mensajes.");
            salida.writeUTF("[SISTEMA]: Escribe 'registrar' para crear una cuenta o 'login' para iniciar sesión.");
        }
        return !limiteAlcanzado;
    }
    
    private void procesarMensajeRegular(String mensaje) throws IOException {
        System.out.println("[" + nombreCliente + "]: " + mensaje);
        actualizarContadorMensajes();
        
        obtenerPartidaActiva()
            .map(partida -> { enviarMensajeEnPartidaSafe(mensaje, partida); return true; })
            .orElseGet(() -> { enviarMensajeGeneralSafe(mensaje); return true; });
    }
    
    private void actualizarContadorMensajes() throws IOException {
        if (autenticado) return;
        
        mensajesEnviados++;
        int restantes = MENSAJES_GRATUITOS - mensajesEnviados;
        String mensajeSistema = restantes > 0 
            ? "[SISTEMA]: Mensaje enviado. Te quedan " + restantes + " mensajes."
            : "[SISTEMA]: Has usado tus 3 mensajes gratuitos. Escribe 'registrar' o 'login' para continuar.";
        salida.writeUTF(mensajeSistema);
    }
    
    private Optional<PartidaGato> obtenerPartidaActiva() {
        return ServidorMulti.obtenerPartidasDeJugador(nombreCliente).stream()
            .filter(p -> !p.isTerminado())
            .findFirst();
    }
    
    private void enviarMensajeEnPartidaSafe(String mensaje, PartidaGato partida) {
        try {
            enviarMensajeEnPartida(mensaje, partida);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void enviarMensajeEnPartida(String mensaje, PartidaGato partida) throws IOException {
        String oponente = partida.getOponente(nombreCliente);
        Optional.ofNullable(ServidorMulti.clientes.get(oponente))
            .ifPresent(cliente -> enviarSafe(cliente, "[CHAT-PARTIDA] " + nombreCliente + ": " + mensaje));
        salida.writeUTF("[CHAT-PARTIDA] Tú: " + mensaje);
    }
    
    private void enviarMensajeGeneralSafe(String mensaje) {
        try {
            enviarMensajeGeneral(mensaje);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void enviarMensajeGeneral(String mensaje) throws IOException {
        String mensajeCompleto = "[" + nombreCliente + "]: " + mensaje;
        ServidorMulti.clientes.values().stream()
            .filter(cliente -> cliente != this && !estaEnPartidaActiva(cliente.nombreCliente))
            .forEach(cliente -> enviarSafe(cliente, mensajeCompleto));
    }
    
    private void enviarSafe(UnCliente cliente, String mensaje) {
        try {
            cliente.salida.writeUTF(mensaje);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    
    private void inicializarCliente() throws IOException {
        enviarMensajeBienvenida();
        nombreCliente = PREFIJO_INVITADO + System.currentTimeMillis();
        ServidorMulti.registrarCliente(nombreCliente, this);
    }
    
    private void enviarMensajeBienvenida() throws IOException {
        salida.writeUTF("=== BIENVENIDO AL CHAT ===");
        salida.writeUTF("Puedes enviar 3 mensajes de prueba antes de registrarte.");
        salida.writeUTF("Escribe 'registrar' o 'login' cuando quieras autenticarte.");
        salida.writeUTF("Escribe 'logout' para cerrar sesión.");
        salida.writeUTF("Escribe 'help' para ver todos los comandos disponibles.");
    }
   
    private boolean estaEnPartidaActiva(String nombreJugador) {
        return ServidorMulti.obtenerPartidasDeJugador(nombreJugador).stream()
            .anyMatch(p -> !p.isTerminado());
    }
    
    private void cambiarNombreCliente(String nuevoNombre) {
        ServidorMulti.clientes.remove(nombreCliente);
        nombreCliente = nuevoNombre;
        ServidorMulti.registrarCliente(nombreCliente, this);
    }
    
    private void notificarATodos(String mensaje, UnCliente remitente) {
        ServidorMulti.clientes.values().stream()
            .filter(cliente -> cliente != remitente && !estaEnPartidaActiva(cliente.nombreCliente))
            .forEach(cliente -> enviarSafe(cliente, "[SISTEMA]: " + mensaje));
    }
    
    
    private void manejarDesconexion() {
        if (nombreCliente != null) {
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
        String usuariosOnline = obtenerUsuariosOnline();
        if (validarUsuariosDisponibles(usuariosOnline, "No hay usuarios online.")) return;
        
        salida.writeUTF("[USUARIOS ONLINE]: " + usuariosOnline);
        salida.writeUTF("[SISTEMA]: Escribe: usuario mensaje");
        
        String[] datosMensaje = leerDatosMensajePrivado();
        if (datosMensaje != null) enviarMensajePrivado(datosMensaje[0], datosMensaje[1]);
    }
    
    private String obtenerUsuariosOnline() {
        return ServidorMulti.clientes.keySet().stream()
            .filter(usuario -> !usuario.equals(nombreCliente) && !usuario.startsWith(PREFIJO_INVITADO))
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }
    
    private String[] leerDatosMensajePrivado() throws IOException {
        String[] partes = entrada.readUTF().trim().split(" ", 2);
        if (partes.length < 2) {
            salida.writeUTF("[ERROR]: Formato incorrecto. Usa: usuario mensaje");
            return null;
        }
        return partes;
    }
    
    private void enviarMensajePrivado(String destino, String mensaje) throws IOException {
        if (validarEnvioPrivado(destino)) {
            Optional.ofNullable(ServidorMulti.clientes.get(destino))
                .ifPresentOrElse(
                    cliente -> {
                        enviarSafe(cliente, "[PRIVADO de " + nombreCliente + "]: " + mensaje);
                        enviarSafe(this, "[Mensaje privado enviado a " + destino + "]: " + mensaje);
                        if (!autenticado) mensajesEnviados++;
                    },
                    () -> enviarSafe(this, "[AVISO]: " + destino + " no está conectado en este momento.")
                );
        }
    }
    
    private boolean validarEnvioPrivado(String destino) throws IOException {
        if (!ServidorMulti.usuarios.containsKey(destino)) {
            salida.writeUTF("[ERROR]: Usuario '" + destino + "' no existe.");
            return false;
        }
        if (!autenticado && mensajesEnviados >= MENSAJES_GRATUITOS) {
            salida.writeUTF("[ERROR]: Debes autenticarte para enviar mensajes privados.");
            return false;
        }
        if (ServidorMulti.estasBloqueado(destino, nombreCliente)) {
            salida.writeUTF("[ERROR]: No puedes enviar mensajes a " + destino + " (bloqueado).");
            return false;
        }
        return true;
    }
    
    
    private void mostrarUsuariosYBloquear() throws IOException {
        if (!verificarAutenticacion()) return;
        
        String usuariosDisponibles = obtenerUsuariosParaBloquear();
        if (validarUsuariosDisponibles(usuariosDisponibles, "No hay usuarios disponibles para bloquear.")) return;
        
        salida.writeUTF("[USUARIOS]: " + usuariosDisponibles);
        salida.writeUTF("[SISTEMA]: Escribe el nombre del usuario:");
        
        String usuarioABloquear = entrada.readUTF().trim();
        if (!usuarioABloquear.isEmpty()) bloquearUsuario(usuarioABloquear);
        else salida.writeUTF("[SISTEMA]: Operación cancelada.");
    }
    
    private String obtenerUsuariosParaBloquear() {
        java.util.List<String> bloqueados = ServidorMulti.obtenerBloqueados(nombreCliente);
        return ServidorMulti.usuarios.keySet().stream()
            .filter(usuario -> !usuario.equals(nombreCliente) && !bloqueados.contains(usuario))
            .map(usuario -> usuario + (ServidorMulti.clientes.containsKey(usuario) ? "[ON]" : "[OFF]"))
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }
    
    private void bloquearUsuario(String usuario) throws IOException {
        if (usuario.equals(nombreCliente)) {
            salida.writeUTF("[ERROR]: No puedes bloquearte a ti mismo.");
            return;
        }
        if (!ServidorMulti.usuarios.containsKey(usuario)) {
            salida.writeUTF("[ERROR]: El usuario '" + usuario + "' no existe.");
            return;
        }
        if (ServidorMulti.estasBloqueado(nombreCliente, usuario)) {
            salida.writeUTF("[ERROR]: Ya tienes bloqueado a " + usuario + ".");
            return;
        }
        
        boolean exito = ServidorMulti.bloquearUsuario(nombreCliente, usuario);
        String mensaje = exito 
            ? "[SISTEMA]: ¡Usuario '" + usuario + "' bloqueado correctamente!"
            : "[ERROR]: No se pudo bloquear al usuario. Intenta de nuevo.";
        salida.writeUTF(mensaje);
        if (exito) System.out.println(nombreCliente + " bloqueó a " + usuario);
    }
    
    private void mostrarBloqueadosYDesbloquear() throws IOException {
        if (!verificarAutenticacion()) return;
        
        java.util.List<String> bloqueados = ServidorMulti.obtenerBloqueados(nombreCliente);
        if (bloqueados.isEmpty()) {
            salida.writeUTF("[SISTEMA]: No tienes usuarios bloqueados.");
            return;
        }
        
        salida.writeUTF("[BLOQUEADOS]: " + formatearListaUsuarios(bloqueados));
        salida.writeUTF("[SISTEMA]: Escribe el nombre del usuario:");
        
        String usuarioADesbloquear = entrada.readUTF().trim();
        if (!usuarioADesbloquear.isEmpty()) desbloquearUsuario(usuarioADesbloquear);
        else salida.writeUTF("[SISTEMA]: Operación cancelada.");
    }
    
    private void desbloquearUsuario(String usuario) throws IOException {
        if (!ServidorMulti.usuarios.containsKey(usuario)) {
            salida.writeUTF("[ERROR]: El usuario '" + usuario + "' no existe.");
            return;
        }
        if (!ServidorMulti.estasBloqueado(nombreCliente, usuario)) {
            salida.writeUTF("[ERROR]: No tienes bloqueado a " + usuario + ".");
            return;
        }
        
        boolean exito = ServidorMulti.desbloquearUsuario(nombreCliente, usuario);
        String mensaje = exito
            ? "[SISTEMA]: ¡Usuario '" + usuario + "' desbloqueado correctamente!"
            : "[ERROR]: No se pudo desbloquear al usuario. Intenta de nuevo.";
        salida.writeUTF(mensaje);
        if (exito) System.out.println(nombreCliente + " desbloqueó a " + usuario);
    }
    
    private void mostrarMisBloqueados() throws IOException {
        if (!verificarAutenticacion()) return;
        
        java.util.List<String> bloqueados = ServidorMulti.obtenerBloqueados(nombreCliente);
        if (bloqueados.isEmpty()) {
            salida.writeUTF("[SISTEMA]: No tienes usuarios bloqueados.");
            return;
        }
        
        salida.writeUTF("[SISTEMA]: === USUARIOS BLOQUEADOS ===");
        for (int i = 0; i < bloqueados.size(); i++) {
            salida.writeUTF((i + 1) + ". " + bloqueados.get(i));
        }
        salida.writeUTF("[SISTEMA]: Total: " + bloqueados.size() + " usuario(s) bloqueado(s)");
    }
    
    private String formatearListaUsuarios(java.util.List<String> usuarios) {
        return usuarios.stream()
            .map(u -> u + (ServidorMulti.clientes.containsKey(u) ? "[ON]" : "[OFF]"))
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }
    
    
    private boolean verificarAutenticacion() throws IOException {
        if (!autenticado) salida.writeUTF("[ERROR]: Debes estar autenticado para usar este comando.");
        return autenticado;
    }
    
    private boolean validarUsuariosDisponibles(String usuarios, String mensajeError) throws IOException {
        if (usuarios.isEmpty()) {
            salida.writeUTF("[SISTEMA]: " + mensajeError);
            return true;
        }
        return false;
    }
    
    
    private void invitarAJugarGato() throws IOException {
        if (!verificarAutenticacion()) return;
        
        if (ServidorMulti.tienePartidaActiva(nombreCliente)) {
            salida.writeUTF("[ERROR]: Ya tienes una partida activa. Solo puedes jugar una partida a la vez.");
            salida.writeUTF("[INFO]: Usa 'partidas' para ver tu partida actual o 'rendirse' para abandonarla.");
            return;
        }
    
        mostrarAyudaGato();
        salida.writeUTF("");
        
        String usuariosDisponibles = obtenerUsuariosParaJugar();
        if (validarUsuariosDisponibles(usuariosDisponibles, "No hay usuarios disponibles para jugar.")) return;
        
        salida.writeUTF("[USUARIOS ONLINE]: " + usuariosDisponibles);
        salida.writeUTF("[SISTEMA]: Escribe el nombre del usuario:");
        
        String invitado = entrada.readUTF().trim();
        if (!invitado.isEmpty()) enviarInvitacionJuego(invitado);
        else salida.writeUTF("[SISTEMA]: Operación cancelada.");
    }
    
    private String obtenerUsuariosParaJugar() {
        return ServidorMulti.clientes.keySet().stream()
            .filter(usuario -> !usuario.equals(nombreCliente) && !usuario.startsWith(PREFIJO_INVITADO))
            .map(usuario -> usuario + (ServidorMulti.tienePartidaActiva(usuario) ? "[OCUPADO]" : ""))
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }
    
    private void enviarInvitacionJuego(String invitado) throws IOException {
        if (!validarInvitacionJuego(invitado)) return;
        
        if (ServidorMulti.enviarInvitacionGato(nombreCliente, invitado)) {
            Optional.ofNullable(ServidorMulti.clientes.get(invitado))
                .ifPresent(cliente -> enviarSafe(cliente, "[GATO]: " + nombreCliente + " te invita a jugar. Escribe 'aceptar' o 'rechazar'."));
            salida.writeUTF("[SISTEMA]: Invitación enviada a " + invitado + ".");
            System.out.println(nombreCliente + " invitó a jugar a " + invitado);
        } else {
            salida.writeUTF("[ERROR]: " + invitado + " ya tiene una invitación pendiente.");
        }
    }
    
    private boolean validarInvitacionJuego(String invitado) throws IOException {
        if (invitado.equals(nombreCliente)) {
            salida.writeUTF("[ERROR]: No puedes jugar contigo mismo.");
            return false;
        }
        if (!ServidorMulti.clientes.containsKey(invitado)) {
            salida.writeUTF("[ERROR]: El usuario no está conectado.");
            return false;
        }
        if (ServidorMulti.tienePartidaActiva(invitado)) {
            salida.writeUTF("[ERROR]: " + invitado + " ya está jugando una partida.");
            return false;
        }
        if (ServidorMulti.obtenerPartida(nombreCliente, invitado) != null) {
            salida.writeUTF("[ERROR]: Ya tienes una partida activa con " + invitado + ".");
            return false;
        }
        return true;
    }
    
    private void aceptarInvitacionGato() throws IOException {
        if (!verificarAutenticacion()) return;
        
        if (ServidorMulti.tienePartidaActiva(nombreCliente)) {
            salida.writeUTF("[ERROR]: Ya tienes una partida activa. Solo puedes jugar una partida a la vez.");
            return;
        }
        
        String invitador = ServidorMulti.obtenerInvitador(nombreCliente);
        if (invitador == null) {
            salida.writeUTF("[ERROR]: No tienes invitaciones pendientes.");
            return;
        }
        
        ServidorMulti.eliminarInvitacion(nombreCliente);
        
        if (!ServidorMulti.clientes.containsKey(invitador)) {
            salida.writeUTF("[ERROR]: El invitador ya no está conectado.");
            return;
        }
        
        if (ServidorMulti.crearPartida(invitador, nombreCliente)) {
            PartidaGato partida = ServidorMulti.obtenerPartida(invitador, nombreCliente);
            
            String primerJugador = partida.getTurnoActual();
            char simboloInvitador = partida.getSimbolo(invitador);
            char simboloInvitado = partida.getSimbolo(nombreCliente);
            
            UnCliente clienteInvitador = ServidorMulti.clientes.get(invitador);
            
            salida.writeUTF("[GATO]: ¡Partida iniciada contra " + invitador + "!");
            salida.writeUTF("[GATO]: Tú eres '" + simboloInvitado + "'");
            if (primerJugador.equals(nombreCliente)) {
                salida.writeUTF("[GATO]: ¡Es TU TURNO!");
            } else {
                salida.writeUTF("[GATO]: Es el turno de " + invitador);
            }
            salida.writeUTF(partida.obtenerTableroTexto());
            salida.writeUTF("");
            salida.writeUTF("=== CHAT DE PARTIDA ACTIVADO ===");
            salida.writeUTF("Los mensajes que escribas solo los verá " + invitador);
            salida.writeUTF("NO recibirás mensajes del chat general mientras juegas.");
            salida.writeUTF("Para volver al chat general, finaliza la partida.");
            salida.writeUTF("");
            salida.writeUTF("=== CÓMO JUGAR ===");
            salida.writeUTF("Escribe: fila columna (ejemplo: 1 2)");
            salida.writeUTF("O también: jugar fila columna (ejemplo: jugar 2 3)");
            salida.writeUTF("");
            salida.writeUTF("Coordenadas del tablero:");
            salida.writeUTF("  Fila 1: posiciones 1 1, 1 2, 1 3 (arriba)");
            salida.writeUTF("  Fila 2: posiciones 2 1, 2 2, 2 3 (centro)");
            salida.writeUTF("  Fila 3: posiciones 3 1, 3 2, 3 3 (abajo)");
            salida.writeUTF("");
            salida.writeUTF("Comandos útiles:");
            salida.writeUTF("  partidas - Ver estado del tablero");
            salida.writeUTF("  rendirse - Abandonar partida");
            
            if (clienteInvitador != null) {
                clienteInvitador.salida.writeUTF("[GATO]: " + nombreCliente + " aceptó tu invitación!");
                clienteInvitador.salida.writeUTF("[GATO]: Tú eres '" + simboloInvitador + "'");
                if (primerJugador.equals(invitador)) {
                    clienteInvitador.salida.writeUTF("[GATO]: ¡Es TU TURNO!");
                } else {
                    clienteInvitador.salida.writeUTF("[GATO]: Es el turno de " + nombreCliente);
                }
                clienteInvitador.salida.writeUTF(partida.obtenerTableroTexto());
                clienteInvitador.salida.writeUTF("");
                clienteInvitador.salida.writeUTF("=== CHAT DE PARTIDA ACTIVADO ===");
                clienteInvitador.salida.writeUTF("Los mensajes que escribas solo los verá " + nombreCliente);
                clienteInvitador.salida.writeUTF("NO recibirás mensajes del chat general mientras juegas.");
                clienteInvitador.salida.writeUTF("Para volver al chat general, finaliza la partida.");
                clienteInvitador.salida.writeUTF("");
                clienteInvitador.salida.writeUTF("=== CÓMO JUGAR ===");
                clienteInvitador.salida.writeUTF("Escribe: fila columna (ejemplo: 1 2)");
                clienteInvitador.salida.writeUTF("O también: jugar fila columna (ejemplo: jugar 2 3)");
                clienteInvitador.salida.writeUTF("");
                clienteInvitador.salida.writeUTF("Coordenadas del tablero:");
                clienteInvitador.salida.writeUTF("  Fila 1: posiciones 1 1, 1 2, 1 3 (arriba)");
                clienteInvitador.salida.writeUTF("  Fila 2: posiciones 2 1, 2 2, 2 3 (centro)");
                clienteInvitador.salida.writeUTF("  Fila 3: posiciones 3 1, 3 2, 3 3 (abajo)");
                clienteInvitador.salida.writeUTF("");
                clienteInvitador.salida.writeUTF("Comandos útiles:");
                clienteInvitador.salida.writeUTF("  partidas - Ver estado del tablero");
                clienteInvitador.salida.writeUTF("  rendirse - Abandonar partida");
            }
        } else {
            salida.writeUTF("[ERROR]: No se pudo crear la partida.");
        }
    }
    
    private void rechazarInvitacionGato() throws IOException {
        if (!verificarAutenticacion()) return;
        
        String invitador = ServidorMulti.obtenerInvitador(nombreCliente);
        if (invitador == null) {
            salida.writeUTF("[ERROR]: No tienes invitaciones pendientes.");
            return;
        }
        
        ServidorMulti.eliminarInvitacion(nombreCliente);
        salida.writeUTF("[SISTEMA]: Invitación rechazada.");
        
        Optional.ofNullable(ServidorMulti.clientes.get(invitador))
            .ifPresent(cliente -> enviarSafe(cliente, "[GATO]: " + nombreCliente + " rechazó tu invitación."));
    }
    
    private void mostrarPartidasActivas() throws IOException {
        if (!verificarAutenticacion()) return;
        
        java.util.List<PartidaGato> partidas = ServidorMulti.obtenerPartidasDeJugador(nombreCliente);
        
        if (partidas.isEmpty()) {
            salida.writeUTF("[SISTEMA]: No tienes partidas activas.");
        } else {
                salida.writeUTF("[SISTEMA]: === TUS PARTIDAS ===");
            for (int i = 0; i < partidas.size(); i++) {
                PartidaGato p = partidas.get(i);
                String oponente = p.getOponente(nombreCliente);
                String estado = p.isTerminado() ? "TERMINADA" : "EN CURSO";
                String turno = p.isTerminado() ? "" : " - Turno de: " + p.getTurnoActual();
                salida.writeUTF((i + 1) + ". vs " + oponente + " [" + estado + "]" + turno);
                salida.writeUTF(p.obtenerTableroTexto());
            }
        }
    }
    
    private void realizarMovimientoGato(String comando) throws IOException {
        if (!verificarAutenticacion()) return;
        
        String[] partes = comando.split("\\s+");
        if (partes.length != 3) {
            salida.writeUTF("[ERROR]: Formato incorrecto. Usa: jugar fila columna (ej: jugar 1 2)");
            return;
        }
        
        int fila, columna;
        try {
            fila = Integer.parseInt(partes[1]) - 1;
            columna = Integer.parseInt(partes[2]) - 1;
        } catch (NumberFormatException e) {
            salida.writeUTF("[ERROR]: Fila y columna deben ser números del 1 al 3.");
            return;
        }
        
        java.util.List<PartidaGato> partidas = ServidorMulti.obtenerPartidasDeJugador(nombreCliente);
        PartidaGato partidaActual = null;
        
        for (PartidaGato p : partidas) {
            if (!p.isTerminado() && p.getTurnoActual().equals(nombreCliente)) {
                partidaActual = p;
                break;
            }
        }
        
        if (partidaActual == null) {
            salida.writeUTF("[ERROR]: No es tu turno en ninguna partida o no tienes partidas activas.");
            return;
        }
        
        if (partidaActual.realizarMovimiento(nombreCliente, fila, columna)) {
            String oponente = partidaActual.getOponente(nombreCliente);
            UnCliente clienteOponente = ServidorMulti.clientes.get(oponente);
            
            salida.writeUTF("[GATO]: Movimiento realizado.");
            salida.writeUTF(partidaActual.obtenerTableroTexto());
            
            if (clienteOponente != null) {
                clienteOponente.salida.writeUTF("[GATO]: " + nombreCliente + " realizó un movimiento.");
                clienteOponente.salida.writeUTF(partidaActual.obtenerTableroTexto());
            }
            
            if (partidaActual.isTerminado()) {
                String ganador = partidaActual.getGanador();
                
                if (ganador.equals("EMPATE")) {
                    salida.writeUTF("[GATO]: ¡EMPATE! La partida terminó en empate.");
                    if (clienteOponente != null) {
                        clienteOponente.salida.writeUTF("[GATO]: ¡EMPATE! La partida terminó en empate.");
                    }
                } else if (ganador.equals(nombreCliente)) {
                    salida.writeUTF("[GATO]: ¡FELICIDADES! ¡HAS GANADO!");
                    if (clienteOponente != null) {
                        clienteOponente.salida.writeUTF("[GATO]: Has perdido. " + ganador + " ganó la partida.");
                    }
                } else {
                    salida.writeUTF("[GATO]: Has perdido. " + ganador + " ganó la partida.");
                    if (clienteOponente != null) {
                        clienteOponente.salida.writeUTF("[GATO]: ¡FELICIDADES! ¡HAS GANADO!");
                    }
                }
                
                salida.writeUTF("[SISTEMA]: Chat de partida desactivado. Tus mensajes ahora van a todos (excepto jugadores en partida).");
                if (clienteOponente != null) {
                    clienteOponente.salida.writeUTF("[SISTEMA]: Chat de partida desactivado. Tus mensajes ahora van a todos (excepto jugadores en partida).");
                }
                
                ServidorMulti.finalizarPartida(partidaActual.getJugador1(), partidaActual.getJugador2());
            } else {
                String turnoActual = partidaActual.getTurnoActual();
                
                salida.writeUTF("[GATO]: Espera el turno de " + oponente);
                
                if (clienteOponente != null) {
                    clienteOponente.salida.writeUTF("[GATO]: ¡Es TU TURNO!");
                }
            }
        } else {
            salida.writeUTF("[ERROR]: Movimiento inválido. La casilla debe estar vacía y en el rango 1-3.");
        }
    }
    
    private void rendirseEnPartida() throws IOException {
        if (!verificarAutenticacion()) return;
        
        java.util.List<PartidaGato> partidas = ServidorMulti.obtenerPartidasDeJugador(nombreCliente);
        PartidaGato partidaActual = null;
        
        for (PartidaGato p : partidas) {
            if (!p.isTerminado()) {
                partidaActual = p;
                break;
            }
        }
        
        if (partidaActual == null) {
            salida.writeUTF("[ERROR]: No tienes partidas activas.");
            return;
        }
        
        String oponente = partidaActual.getOponente(nombreCliente);
        partidaActual.abandonar(nombreCliente);
        
        salida.writeUTF("[GATO]: Te has rendido. " + oponente + " gana la partida.");
        salida.writeUTF("[SISTEMA]: Chat de partida desactivado. Volviste al chat general.");
        
        UnCliente clienteOponente = ServidorMulti.clientes.get(oponente);
        if (clienteOponente != null) {
            clienteOponente.salida.writeUTF("[GATO]: " + nombreCliente + " se rindió. ¡Has ganado!");
            clienteOponente.salida.writeUTF("[SISTEMA]: Chat de partida desactivado. Volviste al chat general.");
        }
        
        ServidorMulti.finalizarPartida(partidaActual.getJugador1(), partidaActual.getJugador2());
        System.out.println(nombreCliente + " se rindió en la partida contra " + oponente);
    }
    
    
    private void mostrarAyuda() throws IOException {
        salida.writeUTF("=== COMANDOS DISPONIBLES ===");
        salida.writeUTF("registrar - Crear una nueva cuenta");
        salida.writeUTF("login - Iniciar sesión");
        salida.writeUTF("logout - Cerrar sesión");
        salida.writeUTF("@ o privado - Enviar mensaje privado");
        salida.writeUTF("bloquear - Bloquear usuario");
        salida.writeUTF("desbloquear - Desbloquear usuario");
        salida.writeUTF("misBloqueados - Ver usuarios bloqueados");
        salida.writeUTF("gato - Jugar al gato");
        salida.writeUTF("help - Mostrar esta ayuda");
    }
    
    private void mostrarAyudaGato() throws IOException {
        salida.writeUTF("=== COMANDOS DEL JUEGO GATO ===");
        salida.writeUTF("gato - Invitar a alguien a jugar");
        salida.writeUTF("aceptar - Aceptar invitación de juego");
        salida.writeUTF("rechazar - Rechazar invitación de juego");
        salida.writeUTF("partidas - Ver tus partidas activas");
        salida.writeUTF("");
        salida.writeUTF("=== DURANTE UNA PARTIDA ===");
        salida.writeUTF("Para hacer una jugada usa:");
        salida.writeUTF("  jugar fila columna  (ejemplo: jugar 1 2)");
        salida.writeUTF("  O simplemente:  fila columna  (ejemplo: 2 3)");
        salida.writeUTF("");
        salida.writeUTF("El tablero usa coordenadas así:");
        salida.writeUTF("     1   2   3");
        salida.writeUTF("  1  ■ | ■ | ■    ← Fila 1");
        salida.writeUTF("    -----------");
        salida.writeUTF("  2  ■ | ■ | ■    ← Fila 2");
        salida.writeUTF("    -----------");
        salida.writeUTF("  3  ■ | ■ | ■    ← Fila 3");
        salida.writeUTF("     ↑   ↑   ↑");
        salida.writeUTF("     C1  C2  C3");
        salida.writeUTF("");
        salida.writeUTF("Ejemplos:");
        salida.writeUTF("  '1 1' = esquina superior izquierda");
        salida.writeUTF("  '2 2' = centro del tablero");
        salida.writeUTF("  '3 3' = esquina inferior derecha");
        salida.writeUTF("");
        salida.writeUTF("rendirse - Abandonar la partida actual");
        salida.writeUTF("");
        salida.writeUTF("NOTA: Mientras juegas NO recibirás mensajes del chat general.");
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
        
        String nombreAnterior = nombreCliente;
        cambiarNombreCliente(nuevoNombre);
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
        
        String nombreAnterior = nombreCliente;
        cambiarNombreCliente(nombre);
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
        cambiarNombreCliente(PREFIJO_INVITADO + System.currentTimeMillis());
        autenticado = false;
        mensajesEnviados = 0;
        
        salida.writeUTF("[SISTEMA]: Has cerrado sesión. Ahora eres: " + nombreCliente);
        salida.writeUTF("[SISTEMA]: Tienes 3 mensajes gratuitos. Escribe 'login' para iniciar sesión nuevamente.");
        System.out.println(nombreAnterior + " cerró sesión y ahora es: " + nombreCliente);
        
        notificarATodos(nombreAnterior + " ha cerrado sesión.", this);
    }
    
    
    @FunctionalInterface
    private interface ComandoHandler {
        boolean ejecutar() throws IOException;
    }
}