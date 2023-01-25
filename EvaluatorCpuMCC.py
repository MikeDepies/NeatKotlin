import melee
from melee.gamestate import GameState, PlayerState
from typing import List, Set
from ActionBehaviorrCPU import ActionBehaviorCPU


class EvaluatorCpuMCC:

    total_damage: float
    total_frames: int
    kills: int
    deaths: int
    total_damage_taken: float
    ground_movement_distance: float
    unique_actions: int
    previous_frame: GameState or None
    last_x: int or None
    last_percent: float or None
    last_opponent_percent: float or None
    player_actions: List[melee.Action]
    player_index: int
    opponent_index: int
    logger: melee.Logger
    excluded_actions : List[melee.Action]
    frame_data = melee.framedata.FrameData()

    def __init__(self, player: int, opponent: int, logger: melee.Logger = None) -> None:
        self.player_index = player
        self.opponent_index = opponent

        self.logger = logger
        self.total_damage = 0
        self.total_damage_taken = 0
        self.unique_actions = 0
        self.kills = 0
        self.deaths = 0
        self.total_frames = 0
        self.ground_movement_distance = 0
        self.previous_frame = None
        self.last_x = None
        self.last_percent = None
        self.last_opponent_percent = None
        self.player_actions = []
        self.excluded_actions = [melee.Action.SHIELD_BREAK_FALL, melee.Action.SHIELD_BREAK_DOWN_D, melee.Action.SHIELD_BREAK_DOWN_U, melee.Action.SHIELD_BREAK_TEETER, melee.Action.SHIELD_BREAK_FLY, melee.Action.SHIELD_BREAK_STAND_D, melee.Action.SHIELD_BREAK_STAND_U,
                                 melee.Action.SPOTDODGE, melee.Action.GROUND_ROLL_SPOT_DOWN, melee.Action.GROUND_SPOT_UP,
                                 melee.Action.DAMAGE_AIR_1, melee.Action.DAMAGE_AIR_2, melee.Action.DAMAGE_AIR_3,
                                 melee.Action.REBOUND, melee.Action.REBOUND_STOP, melee.Action.LANDING_SPECIAL, melee.Action.SHIELD_STUN,
                                 melee.Action.DAMAGE_FLY_HIGH, melee.Action.DAMAGE_FLY_LOW, melee.Action.DAMAGE_FLY_NEUTRAL, melee.Action.DAMAGE_FLY_ROLL,
                                 melee.Action.DAMAGE_FLY_TOP, melee.Action.DAMAGE_GROUND, melee.Action.DAMAGE_HIGH_1, melee.Action.DAMAGE_HIGH_2, melee.Action.DAMAGE_HIGH_3, melee.Action.DAMAGE_ICE, melee.Action.DAMAGE_ICE_JUMP, melee.Action.DAMAGE_LOW_1, melee.Action.DAMAGE_LOW_2, melee.Action.DAMAGE_LOW_3, melee.Action.DAMAGE_NEUTRAL_1,
                                 melee.Action.DAMAGE_NEUTRAL_2, melee.Action.DAMAGE_NEUTRAL_3, melee.Action.DAMAGE_SCREW, melee.Action.DAMAGE_SCREW_AIR,
                                 melee.Action.GRABBED, melee.Action.GRABBED_WAIT_HIGH, melee.Action.GRAB_PUMMELED, melee.Action.LYING_GROUND_DOWN, melee.Action.LYING_GROUND_UP_HIT, melee.Action.LYING_GROUND_UP, melee.Action.FALLING, melee.Action.ON_HALO_DESCENT, melee.Action.ON_HALO_WAIT,
                                 melee.Action.THROWN_BACK, melee.Action.THROWN_F_HIGH, melee.Action.THROWN_F_LOW, melee.Action.THROWN_DOWN, melee.Action.THROWN_DOWN_2, melee.Action.THROWN_FB, melee.Action.THROWN_FF, melee.Action.THROWN_UP, melee.Action.THROWN_FORWARD,
                                 melee.Action.TUMBLING,melee.Action.AIRDODGE, melee.Action.SHIELD_START, melee.Action.SHIELD_RELEASE]

    def is_on_stage(self, game_state: GameState, player: PlayerState) -> bool:
        right_edge_distance = melee.stages.EDGE_GROUND_POSITION[game_state.stage]
        return abs(player.position.x) < right_edge_distance or player.on_ground

    def is_rolling(self, player: PlayerState) -> bool:
        return player.action in [melee.Action.ROLL_BACKWARD, melee.Action.ROLL_FORWARD, melee.Action.SPOTDODGE, melee.Action.GROUND_ROLL_SPOT_DOWN, melee.Action.GROUND_SPOT_UP, melee.Action.AIRDODGE]

    def player_dealt_damage(self, game_state: GameState) -> bool:
        player: PlayerState = game_state.players[self.player_index]
        opponent: PlayerState = game_state.players[self.opponent_index]
        return self.last_opponent_percent < opponent.percent

    def player_took_damage(self, game_state: GameState) -> bool:
        player: PlayerState = game_state.players[self.player_index]
        opponent: PlayerState = game_state.players[self.opponent_index]
        return self.last_percent < player.percent

    def player_damage_amount(self, game_state: GameState) -> float:
        player: PlayerState = game_state.players[self.player_index]
        opponent: PlayerState = game_state.players[self.opponent_index]
        return max(0, opponent.percent - self.last_opponent_percent)

    def player_damage_amount_taken(self, game_state: GameState) -> float:
        player: PlayerState = game_state.players[self.player_index]
        opponent: PlayerState = game_state.players[self.opponent_index]
        return max(0, player.percent - self.last_percent)

    def is_finished(self, game_state: GameState) -> bool:
        player: PlayerState = game_state.players[self.player_index]
        opponent: PlayerState = game_state.players[self.opponent_index]

        return False

    def storeFrameData(self, game_state: GameState) -> None:
        player: PlayerState = game_state.players[self.player_index]
        opponent: PlayerState = game_state.players[self.opponent_index]
        self.total_frames += 1
        self.last_x = player.position.x
        self.last_percent = player.percent
        self.last_opponent_percent = opponent.percent
        self.previous_frame = game_state

    def game_ended(self, game_state: GameState) -> bool:
        player: PlayerState = game_state.players[self.player_index]
        opponent: PlayerState = game_state.players[self.opponent_index]
        return player.stock == 0 or opponent.stock == 0

    def opponent_lost_stock(self, game_state: GameState) -> bool:
        opponent: PlayerState = game_state.players[self.opponent_index]
        opponent_prev_frame: PlayerState = self.previous_frame.players[self.opponent_index]
        return opponent.stock + 1 == opponent_prev_frame.stock

    def player_lost_stock(self, game_state: GameState) -> bool:
        player: PlayerState = game_state.players[self.player_index]
        prev_frame: PlayerState = self.previous_frame.players[self.player_index]
        # if player.stock + 1 == prev_frame.stock:
        #     print("player lost stock!")
        return player.stock + 1 == prev_frame.stock

    def signOf(self, value):
        if (value > 0):
            return 1
        elif value < 0:
            return -1
        else:
            return 0

    def player_ground_move(self, game_state: GameState) -> bool:
        player: PlayerState = game_state.players[self.player_index]
        return not self.frame_data.is_roll(player.character, player.action) and player.speed_ground_x_self != 0

    def evaluate_frame(self, game_state: GameState) -> None:
        if self.last_x is None or game_state.frame < 0:
            self.storeFrameData(game_state)
        else:
            player: PlayerState = game_state.players[self.player_index]
            if self.player_ground_move(game_state):
                self.ground_movement_distance += abs(
                    player.speed_ground_x_self)
            if player.action not in self.player_actions and player.action not in self.excluded_actions:
                self.unique_actions +=1
                self.player_actions.append(player.action)

            if self.player_took_damage(game_state):
                self.total_damage_taken += self.player_damage_amount_taken(
                    game_state)
            if self.player_dealt_damage(game_state):
                self.total_damage += self.player_damage_amount(game_state)
            if self.player_lost_stock(game_state):
                self.deaths += 1
            if self.opponent_lost_stock(game_state):
                self.kills += 1
            # update data to compare for next frame
            self.storeFrameData(game_state)

    def score(self) -> ActionBehaviorCPU:
        return ActionBehaviorCPU(self.kills, self.total_damage, self.deaths, self.total_damage_taken, self.total_frames, self.ground_movement_distance, self.unique_actions)
