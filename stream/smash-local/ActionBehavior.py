
from typing import List


class ActionBehavior:
    actions: List[int]
    kills: List[int]
    damage_actions: List[int]
    recovery_sets: List[List[int]]
    total_damage: float
    total_distance_toward_opponent: float

    def __init__(self, actions: List[int], kills: List[int], damage_actions: List[int], recovery_sets: List[List[int]], total_damage: float, total_distance_toward_opponent: float) -> None:
        self.actions = actions
        self.kills = kills
        self.damage_actions = damage_actions
        self.recovery_sets = recovery_sets
        self.total_damage = total_damage
        self.total_distance_toward_opponent = total_distance_toward_opponent
