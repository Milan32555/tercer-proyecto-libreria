import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../modelos/prestamo.dart';
import '../../servicios/cliente_graphql.dart';
import '../../servicios/servicio_prestamo.dart';
import '../../tema/tema.dart';
import '../../utilidades/formato.dart';
import '../comunes/componentes.dart';
import '../comunes/fichas.dart';
import '../comunes/tabla_datos.dart';

const _tamanioPagina = 10;
const _gris = TextStyle(color: kGris600);

/// TODOS los atributos del préstamo + título y autor de su libro (clase A).
final _columnas = <ColumnaTabla<Prestamo>>[
  ColumnaTabla(titulo: 'Código', ancho: 104, celda: (p) => Text(p.codigo, style: const TextStyle(fontWeight: FontWeight.w600))),
  ColumnaTabla(titulo: 'Ejemplar', ancho: 110, celda: (p) => Text(p.codigoEjemplar)),
  ColumnaTabla(titulo: 'Prestatario', ancho: 160, celda: (p) => Text(p.prestatario, overflow: TextOverflow.ellipsis)),
  ColumnaTabla(titulo: 'Título del libro', ancho: 176, celda: (p) => Text(
    p.tituloLibro ?? '—',
    maxLines: 1,
    overflow: TextOverflow.ellipsis,
    style: const TextStyle(fontFamily: fuenteLibro, fontSize: 15, fontWeight: FontWeight.w500, color: kGris900),
  )),
  ColumnaTabla(titulo: 'Autor del libro', ancho: 168, celda: (p) => Text(p.autorLibro ?? '—', overflow: TextOverflow.ellipsis)),
  ColumnaTabla(titulo: 'Fecha', ancho: 150, celda: (p) => Text(formatearFechaIso(p.fechaPrestamo), style: _gris)),
  ColumnaTabla(titulo: 'Días', ancho: 70, numerica: true, celda: (p) => Text('${p.diasPrestamo}')),
  ColumnaTabla(titulo: 'Multa', ancho: 112, numerica: true, celda: (p) => Text(formatearPrecio(p.multa))),
  ColumnaTabla(titulo: 'Estado', ancho: 136, celda: (p) => InsigniaPrestamo(p.estado)),
  ColumnaTabla(titulo: 'Observaciones', ancho: 180, celda: (p) => Text(p.observaciones ?? '—', style: _gris, overflow: TextOverflow.ellipsis)),
  ColumnaTabla(titulo: 'Versión', ancho: 80, numerica: true, celda: (p) => Text('${p.version}')),
  ColumnaTabla(titulo: 'ID (_id)', ancho: 210, celda: (p) => Text(p.id, style: const TextStyle(fontSize: 12, color: kGris600))),
];

/// Caso de uso: Listar Préstamos con filtro por ejemplar y/o estado. Cada fila trae dos atributos
/// de la clase A (título y autor), que completa el microservicio GraphQL.
class ListarPrestamos extends StatefulWidget {
  const ListarPrestamos({super.key});

  @override
  State<ListarPrestamos> createState() => _ListarPrestamosState();
}

class _ListarPrestamosState extends State<ListarPrestamos> {
  final _ejemplar = TextEditingController();
  String? _estado;
  List<Prestamo> _prestamos = [];
  int _total = 0;
  int _pagina = 0;
  int _totalPaginas = 0;
  bool _cargando = true;
  bool _cargado = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  @override
  void dispose() {
    _ejemplar.dispose();
    super.dispose();
  }

  String? get _filtroEjemplar {
    final texto = _ejemplar.text.trim();
    return texto.isEmpty ? null : texto;
  }

  Future<void> _cargar() async {
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      var resultado = await listarPrestamos(pagina: _pagina, tamanio: _tamanioPagina, codigoEjemplar: _filtroEjemplar, estado: _estado);
      if (resultado.contenido.isEmpty && _pagina > 0 && resultado.totalPaginas > 0) {
        _pagina = resultado.totalPaginas - 1;
        resultado = await listarPrestamos(pagina: _pagina, tamanio: _tamanioPagina, codigoEjemplar: _filtroEjemplar, estado: _estado);
      }
      if (mounted) {
        setState(() {
          _prestamos = resultado.contenido;
          _total = resultado.totalElementos;
          _totalPaginas = resultado.totalPaginas;
        });
      }
    } on ErrorGraphQL catch (e) {
      if (mounted) setState(() => _error = e.mensaje);
    } finally {
      if (mounted) {
        setState(() {
          _cargando = false;
          _cargado = true;
        });
      }
    }
  }

  void _filtrar() {
    FocusManager.instance.primaryFocus?.unfocus();
    _pagina = 0;
    _cargar();
  }

  void _limpiar() {
    _ejemplar.clear();
    setState(() => _estado = null);
    _filtrar();
  }

  void _irAPagina(int pagina) {
    _pagina = pagina;
    _cargar();
  }

  Future<void> _adicionar() async {
    await Navigator.pushNamed(context, '/prestamos/agregar');
    if (mounted) _cargar();
  }

  bool get _hayFiltros => _filtroEjemplar != null || _estado != null;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Listar préstamos')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _adicionar,
        icon: const Icon(LucideIcons.plus, size: 20),
        label: const Text('Adicionar préstamo'),
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(Espacio.e4, Espacio.e3, Espacio.e4, Espacio.e2),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                SearchBar(
                  controller: _ejemplar,
                  hintText: 'Filtrar por código del ejemplar',
                  leading: const Icon(LucideIcons.search, size: 18, color: kGris500),
                  trailing: [
                    if (_hayFiltros)
                      IconButton(tooltip: 'Limpiar filtros', onPressed: _limpiar, icon: const Icon(LucideIcons.x, size: 18)),
                    IconButton(tooltip: 'Filtrar', onPressed: _filtrar, icon: const Icon(LucideIcons.arrowRight, size: 18)),
                  ],
                  textInputAction: TextInputAction.search,
                  onSubmitted: (_) => _filtrar(),
                ),
                const SizedBox(height: Espacio.e3),
                FiltroChips(
                  opciones: estadosPrestamo,
                  seleccionado: _estado,
                  etiquetaDe: etiquetaEstadoPrestamo,
                  textoTodos: 'Todos',
                  alSeleccionar: (estado) {
                    setState(() => _estado = estado);
                    _filtrar();
                  },
                ),
                const SizedBox(height: Espacio.e3),
                ConteoServidor(texto: _total == 1 ? '1 préstamo' : '$_total préstamos'),
              ],
            ),
          ),
          SizedBox(height: 2, child: _cargando ? const LinearProgressIndicator() : null),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: Espacio.e4),
              child: Aviso(
                _error!,
                titulo: 'No se pudo cargar la lista',
                accion: OutlinedButton.icon(
                  onPressed: _cargar,
                  icon: const Icon(LucideIcons.rotateCw, size: 16),
                  label: const Text('Reintentar'),
                ),
              ),
            ),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(Espacio.e4, Espacio.e2, Espacio.e4, 80),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Expanded(
                    child: _prestamos.isEmpty
                        ? (_cargado && _error == null ? _vacio() : const SizedBox.shrink())
                        : TablaDatos<Prestamo>(etiqueta: 'Préstamos de la librería', columnas: _columnas, filas: _prestamos),
                  ),
                  Paginador(pagina: _pagina, totalPaginas: _totalPaginas, alCambiar: _irAPagina, ocupado: _cargando),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _vacio() => _hayFiltros
      ? EstadoVacio(
          icono: LucideIcons.filterX,
          titulo: 'Ningún préstamo coincide con el filtro',
          descripcion: 'Prueba con otro estado o revisa el código del ejemplar.',
          accion: OutlinedButton(onPressed: _limpiar, child: const Text('Limpiar filtros')),
        )
      : const EstadoVacio(
          icono: LucideIcons.handshake,
          titulo: 'Todavía no hay préstamos',
          descripcion: 'Los préstamos que registres aparecerán aquí.',
        );
}
