import math
import melee
from melee.gamestate import GameState, PlayerState, Projectile
import numpy as np


class InputEmbederPacked4:
    frame_data = melee.framedata.FrameData()
    player_index: int
    opponent_index: int
    positionNormalizer: float
    actionNormalizer: float

    def __init__(self, player_index: int, opponent_index: int, positionNormalizer: float, actionNormalizer: float) -> None:
        self.player_index = player_index
        self.opponent_index = opponent_index
        self.positionNormalizer = positionNormalizer
        self.actionNormalizer = actionNormalizer

    def embedCategory(self, state : np.ndarray, statePosition : int, embedValue : int, embedSize : int):
        for i in range(0, embedSize):
            if (i == embedValue):
                state[statePosition + i] = 1
            else:
                state[statePosition + i] = 0

    def applyPlatform(self, platform, state, statePosition):
        if (platform[0]):
            state[statePosition + 0] = platform[0] / self.positionNormalizer
        if (platform[1]):
            state[statePosition + 1] = platform[1] / self.positionNormalizer
        if (platform[2]):
            state[statePosition + 2] = platform[2] / self.positionNormalizer
        return statePosition + 3

    def applyPlayerState(self, player0: PlayerState, state: np.ndarray):
        state[0] = player0.action.value / 397
        state[1] = player0.action_frame / 60
        state[2] = player0.character.value  / 32
        state[3] = player0.ecb.bottom.x / self.positionNormalizer
        state[4] = player0.ecb.bottom.y / self.positionNormalizer
        state[5] = player0.ecb.top.x / self.positionNormalizer
        state[6] = player0.ecb.top.y / self.positionNormalizer
        state[7] = player0.ecb.left.x / self.positionNormalizer
        state[8] = player0.ecb.left.y / self.positionNormalizer
        state[9] = player0.ecb.right.x / self.positionNormalizer
        state[10] = player0.ecb.right.y / self.positionNormalizer
        state[11] = 0
        if player0.facing:
            state[11] = 1
        state[12] = 0
        if player0.hitlag_left:
            state[12] = 1
        state[13] = player0.hitstun_frames_left / 60
        state[14] = player0.invulnerability_left / 60
        state[15] = 0
        if player0.invulnerable:
            state[15] = 1
        state[16] = 0
        if player0.jumps_left > 0:
            state[16] = 1
        state[17] = player0.percent / 100
        state[18] = player0.position.x / self.positionNormalizer
        state[19] = player0.position.y / self.positionNormalizer
        state[20] = player0.shield_strength / 60
        state[21] = player0.stock / 4
        

    def embed_input(self, gamestate: GameState) -> 'list[np.ndarray]':
        state: np.ndarray = np.zeros((2, 23))
        player_action = np.zeros((2, 397))
        player0: PlayerState = gamestate.players[self.player_index]
        self.applyPlayerState(player0, state[0, ...])
        self.embedCategory(player_action[0,...], 0, player0.action.value, 397)
        player1: PlayerState = gamestate.players[self.opponent_index]
        self.applyPlayerState(player1, state[1, ...])
        self.embedCategory(player_action[1,...], 0, player1.action.value, 397)
        edge_position = melee.EDGE_POSITION.get(gamestate.stage, 0)  # Provide a default value of 0 if not found
        state[0, 22] = edge_position / self.actionNormalizer
        state[1, 22] = -edge_position / self.actionNormalizer
        return [state, player_action]
