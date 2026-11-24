import 'package:flutter/material.dart';
import '../servicios/cliente_graphql.dart';
import 'menu_lateral.dart';
import 'comunes/componentes.dart';

class Acceso extends StatelessWidget {
  const Acceso({super.key, required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<String?>(
      valueListenable: ClienteGraphQL().usuario,
      // La pantalla de acceso va fuera del Navigator: el Overlay les da soporte a sus campos de texto.
      builder: (context, usuario, _) => usuario == null
          ? Overlay(initialEntries: [OverlayEntry(builder: (_) => const PantallaAcceso())])
          : child,
    );
  }
}

class PantallaAcceso extends StatefulWidget {
  const PantallaAcceso({super.key});

  @override
  State<PantallaAcceso> createState() => _PantallaAccesoState();
}

class _PantallaAccesoState extends State<PantallaAcceso> {
  final _formulario = GlobalKey<FormState>();
  final _usuario = TextEditingController();
  final _clave = TextEditingController();
  bool _ocupado = false;
  String? _error;

  @override
  void dispose() {
    _usuario.dispose();
    _clave.dispose();
    super.dispose();
  }

  Future<void> _entrar() async {
    if (!_formulario.currentState!.validate()) return;
    setState(() { _ocupado = true; _error = null; });
    try {
      await ClienteGraphQL().iniciarSesion(_usuario.text.trim(), _clave.text);
      _clave.clear();
    } on ErrorGraphQL catch (error) {
      if (mounted) setState(() => _error = error.mensaje);
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: Panel(child: Form(key: _formulario, child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: [
                const Marca(),
                const SizedBox(height: 28),
                const EncabezadoPantalla(titulo: 'Iniciar sesión', descripcion: 'Accede al inventario y los préstamos.'),
                TextFormField(controller: _usuario, decoration: const InputDecoration(labelText: 'Usuario'),
                  autofillHints: const [AutofillHints.username], enabled: !_ocupado,
                  validator: (valor) => valor == null || valor.trim().isEmpty ? 'Ingresa tu usuario.' : null),
                const SizedBox(height: 16),
                TextFormField(controller: _clave, decoration: const InputDecoration(labelText: 'Contraseña'),
                  obscureText: true, autofillHints: const [AutofillHints.password], enabled: !_ocupado,
                  onFieldSubmitted: (_) { if (!_ocupado) _entrar(); },
                  validator: (valor) => valor == null || valor.isEmpty ? 'Ingresa tu contraseña.' : null),
                if (_error != null) ...[const SizedBox(height: 16), Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error))],
                const SizedBox(height: 24),
                FilledButton(onPressed: _ocupado ? null : _entrar,
                  child: Text(_ocupado ? 'Accediendo…' : 'Entrar')),
              ],
            ))),
          ),
        ),
      ),
    );
  }
}
