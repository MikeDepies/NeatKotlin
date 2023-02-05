
from typing import List


class ActionBehavior:
    actions: List[int]
    kills: List[int]
    damage_actions: List[int]
    recovery_sets: List[List[int]]
    total_damage: float
    total_distance_toward_opponent: float
    player_died: bool
    total_frames_hitstun_opponent: int
    total_frames_alive : int
    movement: float

    def __init__(self, actions: List[int], kills: List[int], damage_actions: List[int], recovery_sets: List[List[int]], total_damage: float, total_distance_toward_opponent: float, player_died: bool, total_frames_hitstun_opponent: int, total_frames_alive : int, movement: float) -> None:
        self.actions = actions
        self.kills = kills
        self.damage_actions = damage_actions
        self.recovery_sets = recovery_sets
        self.total_damage = total_damage
        self.total_distance_toward_opponent = total_distance_toward_opponent
        self.player_died = player_died
        self.total_frames_hitstun_opponent = total_frames_hitstun_opponent
        self.total_frames_alive = total_frames_alive
        self.movement = movement
