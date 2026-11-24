import 'package:flutter/material.dart';

import '../../servicios/servicio_prestamo.dart';
import '../../tema/tema.dart';
import '../comunes/componentes.dart';
import 'formulario_prestamo.dart';

/// Caso de uso: Adicionar Préstamo de un ejemplar que ya existe.
class AgregarPrestamo extends StatelessWidget {
  const AgregarPrestamo({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Préstamos')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Espacio.e4, Espacio.e4, Espacio.e4, Espacio.e8),
        children: [
          const EncabezadoPantalla(
            titulo: 'Adicionar préstamo',
            descripcion: 'Registra el préstamo de un ejemplar existente.',
          ),
          FormularioPrestamo(
            textoBoton: 'Adicionar préstamo',
            tituloError: 'No se pudo adicionar el préstamo',
            alGuardar: (input) async {
              final creado = await crearPrestamo(input);
              if (!context.mounted) return;
              ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Préstamo adicionado: ${creado.codigo}.')));
              Navigator.pop(context);
            },
          ),
        ],
      ),
    );
  }
}
