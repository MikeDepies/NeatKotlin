
from typing import List
from dataclasses import dataclass

@dataclass
class ActionBehaviorCPU:
    kills: int
    total_damage: float
    deaths: int
    total_damage_taken: float
    total_frames_alive : int
    ground_movement_distance: float
    unique_actions: int