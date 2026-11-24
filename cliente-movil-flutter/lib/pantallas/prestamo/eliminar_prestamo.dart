import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../modelos/prestamo.dart';
import '../../servicios/cliente_graphql.dart';
import '../../servicios/servicio_prestamo.dart';
import '../../tema/tema.dart';
import '../comunes/componentes.dart';
import '../comunes/confirmar_eliminacion.dart';
import '../comunes/fichas.dart';

/// Caso de uso: Eliminar Préstamo. Se busca, se muestra TODA su información y se confirma.
class EliminarPrestamo extends StatefulWidget {
  const EliminarPrestamo({super.key});

  @override
  State<EliminarPrestamo> createState() => _EliminarPrestamoState();
}

class _EliminarPrestamoState extends State<EliminarPrestamo> {
  final _codigo = TextEditingController();
  Prestamo? _prestamo;
  String? _sinResultado;
  bool _cargando = false;
  String? _error;

  @override
  void dispose() {
    _codigo.dispose();
    super.dispose();
  }

  Future<void> _buscar() async {
    FocusManager.instance.primaryFocus?.unfocus();
    final codigo = _codigo.text.trim();
    setState(() {
      _cargando = true;
      _error = null;
      _sinResultado = null;
      _prestamo = null;
    });
    try {
      final resultado = await buscarPrestamo(codigo);
      if (!mounted) return;
      setState(() {
        _prestamo = resultado;
        if (resultado == null) _sinResultado = 'No hay ningún préstamo con el código «$codigo».';
      });
    } on ErrorGraphQL catch (e) {
      if (mounted) setState(() => _error = e.mensaje);
    } finally {
      if (mounted) setState(() => _cargando = false);
    }
  }

  Future<void> _eliminar() async {
    final prestamo = _prestamo;
    if (prestamo == null) return;
    final confirmado = await confirmarEliminacion(
      context,
      titulo: '¿Eliminar el préstamo ${prestamo.codigo}?',
      descripcion: 'Se eliminará el registro del préstamo. Esta acción no se puede deshacer.',
    );
    if (!confirmado || !mounted) return;

    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      await eliminarPrestamo(prestamo.codigo);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Préstamo eliminado: ${prestamo.codigo}.')));
      Navigator.pop(context);
    } on ErrorGraphQL catch (e) {
      if (mounted) setState(() => _error = e.mensaje);
    } finally {
      if (mounted) setState(() => _cargando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Préstamos')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Espacio.e4, Espacio.e4, Espacio.e4, Espacio.e8),
        children: [
          const EncabezadoPantalla(
            titulo: 'Eliminar préstamo',
            descripcion: 'Busca el préstamo y revisa toda su información antes de eliminarlo.',
          ),
          BuscadorClave(
            controller: _codigo,
            etiqueta: 'Código del préstamo',
            pista: 'PR-001',
            onBuscar: _buscar,
            cargando: _cargando && _prestamo == null,
          ),
          if (_sinResultado != null)
            EstadoVacio(icono: LucideIcons.searchX, titulo: 'Sin resultados', descripcion: _sinResultado),
          if (_error != null)
            Aviso(_error!, titulo: _prestamo != null ? 'El servidor no permitió eliminar el préstamo' : 'No se pudo buscar el préstamo'),
          if (_prestamo != null) ...[
            FichaPrestamo(prestamo: _prestamo!, titulo: 'Préstamo a eliminar'),
            const SizedBox(height: Espacio.e6),
            FilledButton.icon(
              style: FilledButton.styleFrom(backgroundColor: kPeligro600),
              onPressed: _cargando ? null : _eliminar,
              icon: const Icon(LucideIcons.trash2, size: 18),
              label: const Text('Eliminar préstamo'),
            ),
            const SizedBox(height: Espacio.e2),
            OutlinedButton(
              onPressed: () => setState(() {
                _prestamo = null;
                _error = null;
              }),
              child: const Text('Cancelar'),
            ),
          ],
        ],
      ),
    );
  }
}
