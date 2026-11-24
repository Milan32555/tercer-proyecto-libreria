import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../modelos/prestamo.dart';
import '../../servicios/cliente_graphql.dart';
import '../../servicios/servicio_libro.dart' show textoONulo;
import '../../tema/tema.dart';
import '../../utilidades/formato.dart';
import '../../utilidades/validaciones.dart';
import '../comunes/campo_fecha.dart';
import '../comunes/componentes.dart';
import '../comunes/fichas.dart';

/// Campos de un préstamo. Al actualizar, el código y el ejemplar quedan de solo lectura.
class FormularioPrestamo extends StatefulWidget {
  const FormularioPrestamo({
    super.key,
    this.inicial,
    required this.textoBoton,
    required this.tituloError,
    required this.alGuardar,
  });

  final Prestamo? inicial;
  final String textoBoton;
  final String tituloError;
  final Future<void> Function(Map<String, dynamic> input) alGuardar;

  @override
  State<FormularioPrestamo> createState() => _FormularioPrestamoState();
}

class _FormularioPrestamoState extends State<FormularioPrestamo> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _codigo;
  late final TextEditingController _ejemplar;
  late final TextEditingController _prestatario;
  late final TextEditingController _dias;
  late final TextEditingController _multa;
  late final TextEditingController _observaciones;
  late DateTime? _fecha;
  late String _estado;

  bool _intentado = false;
  bool _enviando = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    final p = widget.inicial;
    _codigo = TextEditingController(text: p?.codigo ?? '');
    _ejemplar = TextEditingController(text: p?.codigoEjemplar ?? '');
    _prestatario = TextEditingController(text: p?.prestatario ?? '');
    _dias = TextEditingController(text: '${p?.diasPrestamo ?? 7}');
    _multa = TextEditingController(text: '${p?.multa ?? 0}');
    _observaciones = TextEditingController(text: p?.observaciones ?? '');
    // Sin segundos: si el reloj del celular va un poco adelantado, el servidor no la ve como futura.
    final ahora = DateTime.now();
    _fecha = p == null
        ? DateTime(ahora.year, ahora.month, ahora.day, ahora.hour, ahora.minute)
        : DateTime.tryParse(p.fechaPrestamo);
    _estado = p?.estado ?? estadosPrestamo.first;
  }

  @override
  void dispose() {
    for (final c in [_codigo, _ejemplar, _prestatario, _dias, _multa, _observaciones]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _guardar() async {
    FocusManager.instance.primaryFocus?.unfocus();
    setState(() => _intentado = true);
    final valido = _formKey.currentState!.validate();
    if (!valido || validarFechaNoFutura(_fecha) != null) return;

    setState(() {
      _enviando = true;
      _error = null;
    });
    try {
      await widget.alGuardar({
        'codigo': _codigo.text.trim().toUpperCase(),
        'codigoEjemplar': _ejemplar.text.trim().toUpperCase(),
        'prestatario': _prestatario.text.trim(),
        'fechaPrestamo': aFechaServidor(_fecha!),
        'diasPrestamo': int.parse(_dias.text.trim()),
        'multa': double.parse(_multa.text.trim()),
        'estado': _estado,
        'observaciones': textoONulo(_observaciones.text),
        'version': widget.inicial?.version,
      });
    } on ErrorGraphQL catch (e) {
      if (mounted) setState(() => _error = e.mensaje);
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final editando = widget.inicial != null;
    return Form(
      key: _formKey,
      autovalidateMode: _intentado ? AutovalidateMode.onUserInteraction : AutovalidateMode.disabled,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SeccionFormulario(
            titulo: 'Identificación',
            descripcion: 'El ejemplar debe estar registrado en el inventario.',
            children: [
              TextFormField(
                controller: _codigo,
                readOnly: editando,
                decoration: const InputDecoration(labelText: 'Código del préstamo', hintText: 'PR-001'),
                textCapitalization: TextCapitalization.characters,
                autocorrect: false,
                enableSuggestions: false,
                textInputAction: TextInputAction.next,
                validator: validarCodigoPrestamo,
              ),
              TextFormField(
                controller: _ejemplar,
                readOnly: editando,
                decoration: const InputDecoration(labelText: 'Código del ejemplar', hintText: 'EJ-001'),
                textCapitalization: TextCapitalization.characters,
                autocorrect: false,
                enableSuggestions: false,
                textInputAction: TextInputAction.next,
                validator: validarCodigoEjemplar,
              ),
              TextFormField(
                controller: _prestatario,
                decoration: const InputDecoration(labelText: 'Prestatario'),
                textCapitalization: TextCapitalization.words,
                textInputAction: TextInputAction.next,
                maxLength: 120,
                validator: validarObligatorio,
              ),
            ],
          ),
          SeccionFormulario(
            titulo: 'Condiciones',
            descripcion: 'Fecha, duración, multa y estado del préstamo.',
            children: [
              CampoFecha(
                etiqueta: 'Fecha del préstamo',
                valor: _fecha,
                error: _intentado ? validarFechaNoFutura(_fecha) : null,
                alCambiar: (fecha) => setState(() => _fecha = fecha),
              ),
              TextFormField(
                controller: _dias,
                decoration: const InputDecoration(labelText: 'Días del préstamo'),
                keyboardType: TextInputType.number,
                textInputAction: TextInputAction.next,
                validator: validarDiasPrestamo,
              ),
              TextFormField(
                controller: _multa,
                decoration: const InputDecoration(labelText: 'Multa', prefixText: '\$ '),
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                textInputAction: TextInputAction.next,
                validator: validarDecimalNoNegativo,
              ),
              DropdownButtonFormField<String>(
                initialValue: _estado,
                decoration: const InputDecoration(labelText: 'Estado'),
                style: const TextStyle(fontFamily: fuenteUi, fontSize: 16, color: kGris900),
                icon: const Icon(LucideIcons.chevronDown, size: 18),
                items: [for (final e in estadosPrestamo) DropdownMenuItem(value: e, child: InsigniaPrestamo(e))],
                onChanged: (e) => setState(() => _estado = e!),
              ),
              TextFormField(
                controller: _observaciones,
                decoration: const InputDecoration(labelText: 'Observaciones (opcional)', alignLabelWithHint: true),
                minLines: 2,
                maxLines: 4,
                maxLength: 500,
                validator: validarLargoMaximo(500),
              ),
            ],
          ),
          if (_error != null) Aviso(_error!, titulo: widget.tituloError),
          const SizedBox(height: Espacio.e6),
          FilledButton.icon(
            onPressed: _enviando ? null : _guardar,
            icon: _enviando
                ? const SizedBox.square(dimension: 16, child: CircularProgressIndicator(strokeWidth: 2, color: kGris500))
                : const Icon(LucideIcons.check, size: 18),
            label: Text(widget.textoBoton),
          ),
        ],
      ),
    );
  }
}
