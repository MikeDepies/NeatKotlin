import melee
from melee.gamestate import GameState, PlayerState

from ActionBehaviorrCPU import ActionBehaviorCPU


class EvaluatorCpuMCC:

    total_damage: float
    total_frames: int
    kills: int
    deaths: int
    total_damage_taken: float
    previous_frame: GameState or None
    last_x: int or None
    last_percent: float or None
    last_opponent_percent: float or None

    player_index: int
    opponent_index: int
    logger: melee.Logger

    frame_data = melee.framedata.FrameData()

    def __init__(self, player: int, opponent: int, logger: melee.Logger = None) -> None:
        self.player_index = player
        self.opponent_index = opponent

        self.logger = logger
        self.total_damage = 0
        self.total_damage_taken = 0
        self.kills = 0
        self.deaths = 0
        self.total_frames = 0
        self.previous_frame = None
        self.last_x = None
        self.last_percent = None
        self.last_opponent_percent = None

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

    def evaluate_frame(self, game_state: GameState) -> None:
        if self.last_x is None or game_state.frame < 0:
            self.storeFrameData(game_state)
        else:
            print("processing frame")
            
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
        return ActionBehaviorCPU(self.kills, self.total_damage, self.deaths, self.total_damage_taken, self.total_frames)
