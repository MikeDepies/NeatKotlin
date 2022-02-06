from argparse import Action
import math
from typing import List, Set
import melee

from melee.gamestate import GameState, PlayerState

from ActionBehavior import ActionBehavior


class Evaluator:
    frames_without_damage: int
    actions_without_damage: int
    total_damage: float
    knocked: bool
    opponent_knocked: bool
    opponent_touched_ground: bool
    knocked_off_stage: bool
    opponent_knocked_off_stage: bool
    frames_since_opponent_unknocked: int
    actions: List[int]
    damage_actions: List[int]

    kill_actions: List[int] 
    recovery_actions_set: List[List[int]] 
    recovery_actions: List[List[int]] 
    damage_since_recovery: bool 
    never_shielded: bool 
    total_frames: int 
    off_stage_time: int 
    total_distanceTowardOpponent: float 
    previous_frame: GameState or None
    last_x: int or None 
    last_percent: float or None 
    last_opponent_percent: float or None 
    player_previous_actions: List[int] 
    player_previous_action: melee.Action or None
    opponent_previous_action: melee.Action or None
    last_damage_action: melee.Action or None
    action_limit: int
    attack_timer: int
    max_timer: int
    player_index: int
    opponent_index: int
    logger: melee.Logger
    player_died: bool

    def __init__(self, player: int, opponent: int, attack_timer: int = 5, max_timer: int = 60, action_limit: int = 5, logger: melee.Logger = None) -> None:
        self.player_index = player
        self.opponent_index = opponent
        self.attack_timer = attack_timer
        self.max_timer = max_timer
        self.action_limit = action_limit
        self.logger = logger
        self.frames_without_damage = 0
        self.actions_without_damage = 0
        self.total_damage = 0
        self.knocked = False
        self.opponent_knocked = False
        self.opponent_touched_ground = False
        self.knocked_off_stage = False
        self.opponent_knocked_off_stage = False
        self.frames_since_opponent_unknocked= 0
        self.actions =  []
        self.damage_actions = []
        self.kill_actions= []
        self.recovery_actions_set = []
        self.recovery_actions= []
        self.damage_since_recovery = True
        self.never_shielded = True
        self.total_frames = 0
        self.off_stage_time= 0
        self.total_distanceTowardOpponent= 0
        self.previous_frame= None
        self.last_x= None
        self.last_percent = None
        self.last_opponent_percent= None
        self.player_previous_actions = list()
        self.last_damage_action = None
        self.player_died = False
    # def log(self, log_message : str):
    #     self.logger.writeframe()

    def is_on_stage(self, game_state: GameState, player: PlayerState) -> bool:
        right_edge_distance = melee.stages.EDGE_GROUND_POSITION[game_state.stage]
        return abs(player.position.x) < right_edge_distance or player.on_ground

    def is_rolling(self, player: PlayerState) -> bool:
        return player.action in [melee.Action.ROLL_BACKWARD, melee.Action.ROLL_FORWARD]

    def capture_action(self, player: PlayerState):
        if player.action.value in self.player_previous_actions or self.knocked_off_stage:
            return False
        else:
            self.player_previous_actions.append(player.action.value)
            if (len(self.player_previous_actions) > self.action_limit):
                self.player_previous_actions.pop(0)
            return True

    def player_dealt_damage(self, game_state: GameState) -> bool:
        player: PlayerState = game_state.players[self.player_index]
        opponent: PlayerState = game_state.players[self.opponent_index]
        return self.last_opponent_percent < opponent.percent

    def player_damage_amount(self, game_state: GameState) -> float:
        player: PlayerState = game_state.players[self.player_index]
        opponent: PlayerState = game_state.players[self.opponent_index]
        return max(0, opponent.percent - self.last_opponent_percent)

    def is_finished(self, game_state: GameState) -> bool:
        player: PlayerState = game_state.players[self.player_index]
        opponent: PlayerState = game_state.players[self.opponent_index]

        player_on_stage = self.is_on_stage(game_state, player)
        opponent_on_stage = self.is_on_stage(
            game_state, opponent) or opponent.action == melee.Action.EDGE_HANGING
        attack_timer_elapsed = self.frames_without_damage / \
            60 > self.attack_timer and (player_on_stage and opponent_on_stage)
        max_timer_elapsed = self.total_frames / 60 > self.max_timer
        return attack_timer_elapsed or max_timer_elapsed or self.player_died

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
        if player.stock +1 == prev_frame.stock:
            print("player lost stock!")
        return player.stock +1 == prev_frame.stock

    def signOf(self, value):
        if (value > 0):
            return 1
        elif value < 0:
            return -1
        else:
            return 0

    def evaluate_frame(self, game_state: GameState) -> None:
        player: PlayerState = game_state.players[self.player_index]
        opponent: PlayerState = game_state.players[self.opponent_index]
        if self.last_x is None or game_state.frame < 0:
            self.storeFrameData(game_state)
        else:

            # need handling for game ending and new one starting
            # stocks and other values get reset...

            knockback_combined_speed = abs(
                player.speed_x_attack) + abs(player.speed_y_attack)
            off_stage = not self.is_on_stage(game_state, player)
            on_stage = not off_stage and player.position.y >= 0 or player.action == melee.Action.EDGE_HANGING
            if off_stage or player.position.y < 0:
                self.off_stage_time += 1
            else:
                self.off_stage_time = 0

            if not self.knocked:
                self.knocked = knockback_combined_speed > 0
            elif player.on_ground:
                self.knocked = False
            if self.never_shielded and player.action == melee.Action.SHIELD:
                self.never_shielded = False
            if off_stage and self.knocked:
                self.knocked_off_stage = True
            x_diff = player.position.x - self.last_x
            x_diff_opponent = opponent.position.x - player.position.x
            toward_opponent = self.signOf(
                x_diff) == self.signOf(x_diff_opponent)
            if toward_opponent and not self.is_rolling(player):
                self.total_distanceTowardOpponent += abs(x_diff)

            if on_stage and self.knocked_off_stage and self.damage_since_recovery:
                self.frames_without_damage = 0
                self.knocked_off_stage = False
                self.damage_since_recovery = False
                self.recovery_actions_set.append(self.recovery_actions)
                self.recovery_actions = []
            opponent_knockback_combined_speed = abs(
                opponent.speed_x_attack) + abs(opponent.speed_y_attack)
            if not self.opponent_knocked:
                if opponent_knockback_combined_speed > 0:
                    self.opponent_knocked = True
                    self.opponent_touched_ground = False
            elif opponent.on_ground:
                self.opponent_touched_ground = True

            if self.opponent_touched_ground and self.opponent_knocked:
                self.frames_since_opponent_unknocked += 1

            # grace period for opponent landing. Allowing for combos to continue under the same string.
            if self.frames_since_opponent_unknocked > 90:
                self.opponent_knocked = False
                self.frames_since_opponent_unknocked = 0

            opponent_off_stage = not self.is_on_stage(game_state, opponent)
            opponent_on_stage = not opponent_off_stage and opponent.position.y >= 0
            if opponent_off_stage and self.opponent_knocked:
                self.opponent_knocked_off_stage = True

            if opponent_on_stage and self.opponent_knocked_off_stage:
                self.opponent_knocked_off_stage = False

            if not player.invulnerable and not self.opponent_knocked and not opponent.invulnerable and not opponent.hitstun_frames_left > 0 or self.is_rolling(player) or self.is_rolling(opponent):
                self.frames_without_damage += 1

            if self.player_dealt_damage(game_state):
                self.damage_since_recovery = True
                self.frames_without_damage = 0
                self.actions_without_damage = 0
                self.total_damage += self.player_damage_amount(game_state)
                self.damage_actions.append(player.action.value)
                self.last_damage_action = player.action

            if self.previous_frame and self.previous_frame.players[self.player_index].action != player.action:
                if self.capture_action(player):
                    self.actions.append(player.action.value)
                if self.knocked_off_stage:
                    self.recovery_actions.append(player.action.value)
            if self.player_lost_stock(game_state):
                self.player_died = True

            if self.opponent_lost_stock(game_state) and self.opponent_knocked:
                previous_frame_opponent: PlayerState = self.previous_frame.players[
                    self.opponent_index]
                self.kill_actions.append(previous_frame_opponent.action.value)
                if self.last_damage_action is not None:
                    self.kill_actions.append(self.last_damage_action.value)
                self.frames_without_damage = -60 * 4
                self.actions_without_damage = 0

            # update data to compare for next frame
            self.storeFrameData(game_state)

    def score(self, game_state: GameState) -> ActionBehavior:
        return ActionBehavior(self.actions, self.kill_actions,
                              self.damage_actions, self.recovery_actions_set,
                              self.total_damage, self.total_distanceTowardOpponent)
