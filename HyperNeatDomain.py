
from dataclasses import dataclass
from typing import Dict, List


@dataclass
class LayerPlane:
    height : int
    width: int
    id: str

@dataclass
class LayerShape3D:
    layer_plane: LayerPlane
    x_origin: int
    y_origin: int
    z_origin: int

@dataclass
class HyperNetworkShape:
    width : int
    height : int
    depth : int

@dataclass
class NetworkDesign:
    connection_planes: List[LayerShape3D]
    connection_relationships: Dict[str, List[str]]
    target_connection_mapping: Dict[str, List[str]]
    calculation_order: List[str]

@dataclass
class HyperDimension3D:
    x_min: float
    x_max: float
    y_min: float
    y_max: float
    z_min: float
    z_max: float