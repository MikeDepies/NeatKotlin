from argparse import Action
import math
from typing import List, Set
import melee

from melee.gamestate import GameState, PlayerState

from ActionBehavior import ActionBehavior


class Evaluator:
    frames_without_damage: float
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
    player_sd: bool
    frame_data = melee.framedata.FrameData()
    damage_action_available: bool
    total_frames_hitstun: int
    hitstun_velocity: int
    total_frames_alive: int
    movement_frames: int

    def __init__(self, player: int, opponent: int, attack_timer: int, max_timer: int, action_limit: int, logger: melee.Logger = None) -> None:
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
        self.frames_since_opponent_unknocked = 0
        self.actions = []
        self.damage_actions = []
        self.kill_actions = []
        self.recovery_actions_set = []
        self.recovery_actions = []
        self.damage_since_recovery = True
        self.never_shielded = True
        self.total_frames = 0
        self.off_stage_time = 0
        self.total_distanceTowardOpponent = 0
        self.previous_frame = None
        self.last_x = None
        self.last_percent = None
        self.last_opponent_percent = None
        self.player_previous_actions = list()
        self.last_damage_action = None
        self.player_died = False
        self.player_sd = False
        self.damage_action_available = True
        self.total_frames_hitstun = 0
        self.hitstun_velocity = 0
        self.total_frames_alive = 0
        self.movement_frames = 0
        self.actions_satisfied = True
        self.current_damage = 0
        self.excluded_actions = []
        self.total_deaths = 0
        self.excluded_actions2 = [melee.Action.SHIELD_BREAK_FALL, melee.Action.SHIELD_BREAK_DOWN_D, melee.Action.SHIELD_BREAK_DOWN_U, melee.Action.SHIELD_BREAK_TEETER, melee.Action.SHIELD_BREAK_FLY, melee.Action.SHIELD_BREAK_STAND_D, melee.Action.SHIELD_BREAK_STAND_U,
                                 melee.Action.CROUCH_START, melee.Action.CROUCH_END, melee.Action.GROUND_ROLL_SPOT_DOWN, melee.Action.GROUND_SPOT_UP,
                                 melee.Action.DAMAGE_AIR_1, melee.Action.DAMAGE_AIR_2, melee.Action.DAMAGE_AIR_3,
                                 melee.Action.REBOUND, melee.Action.REBOUND_STOP, melee.Action.LANDING_SPECIAL, melee.Action.SHIELD_STUN,
                                 melee.Action.DAMAGE_FLY_HIGH, melee.Action.DAMAGE_FLY_LOW, melee.Action.DAMAGE_FLY_NEUTRAL, melee.Action.DAMAGE_FLY_ROLL,
                                 melee.Action.DAMAGE_FLY_TOP, melee.Action.DAMAGE_GROUND, melee.Action.DAMAGE_HIGH_1, melee.Action.DAMAGE_HIGH_2, melee.Action.DAMAGE_HIGH_3, melee.Action.DAMAGE_ICE, melee.Action.DAMAGE_ICE_JUMP, melee.Action.DAMAGE_LOW_1, melee.Action.DAMAGE_LOW_2, melee.Action.DAMAGE_LOW_3, melee.Action.DAMAGE_NEUTRAL_1,
                                 melee.Action.DAMAGE_NEUTRAL_2, melee.Action.DAMAGE_NEUTRAL_3, melee.Action.DAMAGE_SCREW, melee.Action.DAMAGE_SCREW_AIR,
                                 melee.Action.GRABBED, melee.Action.GRABBED_WAIT_HIGH, melee.Action.GRAB_PUMMELED, melee.Action.LYING_GROUND_DOWN, melee.Action.LYING_GROUND_UP_HIT, melee.Action.LYING_GROUND_UP, melee.Action.FALLING, melee.Action.ON_HALO_DESCENT, melee.Action.ON_HALO_WAIT,
                                 melee.Action.THROWN_BACK, melee.Action.THROWN_F_HIGH, melee.Action.THROWN_F_LOW, melee.Action.THROWN_DOWN, melee.Action.THROWN_DOWN_2, melee.Action.THROWN_FB, melee.Action.THROWN_FF, melee.Action.THROWN_UP, melee.Action.THROWN_FORWARD,
                                 melee.Action.TUMBLING, melee.Action.SHIELD_START, melee.Action.SHIELD_RELEASE, melee.Action.LOOPING_ATTACK_MIDDLE, melee.Action.LOOPING_ATTACK_END, melee.Action.LANDING,
                                 melee.Action.FAIR_LANDING, melee.Action.BAIR_LANDING, melee.Action.LANDING_SPECIAL, melee.Action.NAIR_LANDING, melee.Action.DAIR_LANDING, melee.Action.UAIR_LANDING,
                                 melee.Action.DEAD_FALL, melee.Action.FALLING, melee.Action.FALLING_BACKWARD, melee.Action.FALLING_BACKWARD, melee.Action.SPECIAL_FALL_BACK, melee.Action.SPECIAL_FALL_FORWARD]
    # def log(self, log_message : str):
    #     self.logger.writeframe()

    def is_on_stage(self, game_state: GameState, player: PlayerState) -> bool:
        right_edge_distance = melee.stages.EDGE_GROUND_POSITION[game_state.stage]
        return abs(player.position.x) < right_edge_distance or player.on_ground

    def is_rolling(self, player: PlayerState) -> bool:
        return player.action in [melee.Action.ROLL_BACKWARD, melee.Action.ROLL_FORWARD, melee.Action.SPOTDODGE, melee.Action.GROUND_ROLL_SPOT_DOWN, melee.Action.GROUND_SPOT_UP, melee.Action.AIRDODGE]

    def capture_action(self, player: PlayerState):
        if player.action.value in self.player_previous_actions or player.action in self.excluded_actions:
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

        player_on_stage = self.is_on_stage(game_state, player)
        opponent_on_stage = self.is_on_stage(
            game_state, opponent) or opponent.action == melee.Action.EDGE_HANGING
        attack_timer_elapsed = self.frames_without_damage / \
            60 > self.attack_timer and (
                not self.knocked and player_on_stage and opponent_on_stage)
        max_timer_elapsed = self.total_frames / 60 > self.max_timer
        if max_timer_elapsed:
            print("max_timer_elapsed: " + str(self.total_frames) +
                  " / 60 -> " + str(self.max_timer))
        if attack_timer_elapsed:
            print("attack_timer_elapsed: " + str(self.frames_without_damage) +
                  " / 60 -> " + str(self.attack_timer))
        if attack_timer_elapsed:
            self.player_sd = True
        # if self.player_died:
        #     print("player " + str(self.player_index) + " died.")
        # player.stock == 0 #
        return attack_timer_elapsed or max_timer_elapsed or player.stock == 0 or self.player_sd

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
            # or player.action == melee.Action.EDGE_HANGING
            on_stage = not off_stage and player.position.y >= 0
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
            if not self.knocked:
                self.total_frames_alive += pow(max(0, (1 - abs(player.position.x / (
                    melee.EDGE_POSITION.get(game_state.stage) /3 ))) * 2), 2)
            # print(str(pow(max(0, 1 - abs(player.x / (melee.EDGE_POSITION.get(game_state.stage) / 3))), 2)))
            toward_opponent = self.signOf(
                x_diff) == self.signOf(x_diff_opponent)
            # and not self.frame_data.is_roll(player.character, player.action)
            # and not self.frame_data.is_roll(player.character, player.action) and not self.frame_data.is_bmove(player.character, player.action)and not self.frame_data.is_attack(player.character, player.action)
            if toward_opponent and not self.frame_data.is_roll(player.character, player.action):
                self.total_distanceTowardOpponent += abs(x_diff)
            if opponent.hitstun_frames_left > 2 and opponent.action not in [melee.Action.GRABBED, melee.Action.GRAB_PUMMELED]:
                if self.hitstun_velocity < 1:
                    self.hitstun_velocity = 1
                elif self.hitstun_velocity < 5:
                    self.hitstun_velocity += 1/60
                # print("action: " + str(opponent.action) + " -> ( " + str(opponent.hitstun_frames_left) + ") - " + str(opponent.hitlag_left))
                self.total_frames_hitstun += self.hitstun_velocity
            else:
                self.hitstun_velocity -= .1
            if on_stage and self.knocked_off_stage:
                if self.damage_since_recovery:
                    # self.frames_without_damage -= 60 * self.attack_timer
                    self.damage_since_recovery = False
                self.knocked_off_stage = False
                self.knocked = False
                if (len(self.recovery_actions) > 0):
                    self.recovery_actions_set.append(self.recovery_actions)
                self.recovery_actions = []
            opponent_knockback_combined_speed = abs(
                opponent.speed_x_attack) + abs(opponent.speed_y_attack)
            if not self.opponent_knocked:
                if opponent_knockback_combined_speed > 0 or opponent.action == melee.Action.YOSHI_EGG:
                    self.opponent_knocked = True
                    self.opponent_touched_ground = False
            elif opponent.on_ground and opponent_knockback_combined_speed == 0:
                self.opponent_touched_ground = True

            if self.opponent_touched_ground and self.opponent_knocked:
                self.frames_since_opponent_unknocked += 1

            # grace period for opponent landing. Allowing for combos to continue under the same string.
            if self.frames_since_opponent_unknocked > 10:
                self.opponent_knocked = False
                self.frames_since_opponent_unknocked = 0

            #     # print("movement: " + str(self.movement_frames))
            #     if self.movement_frames > 15:
            #         if len(self.player_previous_actions) > 0:
            #             a = self.player_previous_actions.pop(0)
            #             print("popped action: " + str(a) )
            #         self.movement_frames = 0
            opponent_off_stage = not self.is_on_stage(game_state, opponent)
            opponent_on_stage = not opponent_off_stage and opponent.position.y >= 0
            if opponent_off_stage and self.opponent_knocked:
                self.opponent_knocked_off_stage = True

            if opponent_on_stage and self.opponent_knocked_off_stage:
                self.opponent_knocked_off_stage = False
            if player.speed_ground_x_self != 0 and not (player.action in self.excluded_actions or self.frame_data.is_roll(player.character, player.action) or self.frame_data.is_shield(player.action) or self.frame_data.is_bmove(player.character, player.action) or self.frame_data.is_attack(player.character, player.action) or self.frame_data.is_grab(player.character, player.action)):

                # print(str(player.character) + " - " + str(player.action))
                # print(player.speed_ground_x_self)
                self.movement_frames += abs(player.speed_ground_x_self)

            #     # self.total_distanceTowardOpponent += abs(player.speed_ground_x_self / 10)
                # self.frames_without_damage -= abs(player.speed_ground_x_self)
                # print(abs(player.speed_ground_x_self))
                # pass
            # if not player.invulnerable and not self.opponent_knocked and not opponent.invulnerable and not self.knocked or (self.frame_data.is_roll(player.character, player.action) or self.frame_data.is_roll(opponent.character, opponent.action)):
            # if (not self.knocked or game_state.frame % 10 == 0) and not player.invulnerable and not opponent.invulnerable:
            if  not self.opponent_knocked or self.opponent_knocked and game_state.distance < 10:
                if not player.invulnerable or self.frame_data.is_roll(player.character, player.action):
                    if not opponent.invulnerable or self.frame_data.is_roll(opponent.character, opponent.action):    
                        # if not self.knocked:
                        self.frames_without_damage += 1
            if self.opponent_knocked:
                self.frames_without_damage -= 4
            # if self.knocked:
            #     self.frames_without_damage -=2

            # if self.player_took_damage(game_state):
            #         self.frames_without_damage -= (60 * self.attack_timer)
            self.current_damage = opponent.percent
            if self.player_dealt_damage(game_state):
                self.damage_since_recovery = True
                # if game_state.distance < 22:
                

                self.actions_without_damage = 0
                # self.player_previous_actions.clear()
                self.total_damage += self.player_damage_amount(game_state)
                # if self.damage_action_available:
                self.damage_actions.append(player.action.value)
                    # self.damage_action_available = False
                # self.frames_without_damage -= 60 * self.attack_timer
                if self.actions_satisfied:
                    self.actions_satisfied = False
                self.last_damage_action = player.action

            # if  self.previous_frame and (self.previous_frame.players[self.player_index].action != player.action) and  (self.frame_data.is_bmove(game_state.players[self.player_index].character, game_state.players[self.player_index].action) or self.frame_data.is_attack(game_state.players[self.player_index].character, game_state.players[self.player_index].action) or self.frame_data.is_grab(game_state.players[self.player_index].character, game_state.players[self.player_index].action)):
            #     self.frames_without_damage += 60
            
            # if self.frame_data.is_roll(player.character, player.action) or self.frame_data.is_shield(player.action):
            #     self.frames_without_damage += 6
            frame_mod = 4
            if player.action == melee.Action.WALK_SLOW:
                frame_mod = 20
            elif player.action == melee.Action.WALK_MIDDLE:
                frame_mod = 20
            elif player.action == melee.Action.WALK_FAST:
                frame_mod = 20
            elif player.action == melee.Action.RUNNING:
                frame_mod = 2
            elif player.action == melee.Action.DASHING:
                frame_mod = 2
            elif player.action == melee.Action.RUN_DIRECT:
                frame_mod = 2
            
            move_capture = player.action_frame != 1 and player.action_frame % frame_mod == 0 and player.action in [melee.Action.WALK_FAST, melee.Action.WALK_MIDDLE, melee.Action.WALK_SLOW, melee.Action.RUNNING, melee.Action.RUN_DIRECT, melee.Action.DASHING, melee.Action.SHOULDERED_WALK_SLOW, melee.Action.SHOULDERED_WALK_MIDDLE, melee.Action.SHOULDERED_WALK_FAST]
            if self.previous_frame and (self.previous_frame.players[self.player_index].action != player.action or move_capture):
                # self.frames_without_damage += 15
                self.damage_action_available = True
                action_capture = self.capture_action(player)
                if action_capture or move_capture:
                    # print("prev actions:")
                    # print(self.player_previous_actions)
                    # self.frames_without_damage -= 2
                    
                    if action_capture:
                        self.actions_satisfied = True
                        # self.frame_data.frame_count(player.character,player.action)
                        # self.frames_without_damage -= 60 * self.attack_timer
                        # self.frames_without_damage -= 30
                        self.actions.append(player.action.value)
                    # elif self.frame_data.is_attack(player.character, player.action):
                    #     self.frames_without_damage += 60
                if self.knocked_off_stage and player.action not in self.excluded_actions or player.action == melee.Action.AIRDODGE:
                    self.recovery_actions.append(player.action.value)
            if self.player_lost_stock(game_state):
                self.recovery_actions.clear()
                # if player.stock == 0:
                # if not self.knocked:
                #     self.actions.clear()
                self.frames_without_damage += (10) * 60
                # self.max_timer -= 20
                # if not self.knocked:
                #     self.player_sd = True
                self.player_died = True
                self.knocked_off_stage = False
                self.total_deaths+=1
            #     self.knocked = False
            # if self.opponent_lost_stock(game_state):
            #     self.frames_without_damage -= 60 * self.attack_timer/2
            if self.opponent_lost_stock(game_state) and self.opponent_knocked:
                previous_frame_opponent: PlayerState = self.previous_frame.players[
                    self.opponent_index]
                # self.kill_actions.append(previous_frame_opponent.action.value)
                if self.last_damage_action is not None:
                    self.kill_actions.append(self.last_damage_action.value)
                
                self.actions_without_damage -= self.attack_timer * 60
                # self.total_frames -= 30*60
                # self.max_timer += 20
                # if self.total_frames < 0:
                #     self.total_frames = 0
                self.opponent_knocked = False
            # print(self.frames_without_damage)
            # update data to compare for next frame
            self.frames_without_damage = max(
                self.frames_without_damage,  -6 * self.attack_timer * 60)
            self.storeFrameData(game_state)

    def score(self, game_state: GameState) -> ActionBehavior:
        return ActionBehavior(self.actions, self.kill_actions,
                              self.damage_actions, self.recovery_actions_set,
                              self.current_damage, self.total_frames_alive, self.player_sd, int(self.total_frames_hitstun), self.total_frames, self.movement_frames, self.total_deaths)
