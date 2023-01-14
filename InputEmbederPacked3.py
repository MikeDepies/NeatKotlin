import math
import melee
from melee.gamestate import GameState, PlayerState, Projectile
import numpy as np


class InputEmbederPacked3:
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

    def embedCategory(self, state, statePosition, embedValue, embedSize):
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

    def applyPlayerState(self, player0: PlayerState, state: np.ndarray, statePosition: int):
        state[statePosition + 0] = player0.speed_air_x_self / 2.5
        state[statePosition + 1] = player0.speed_ground_x_self / 2.5
        state[statePosition + 2] = player0.speed_x_attack / 2.5
        state[statePosition + 3] = player0.speed_y_attack / 2.5
        state[statePosition + 4] = player0.speed_y_self / 2.5

        state[statePosition +
              5] = ((player0.percent / self.positionNormalizer))
        state[statePosition + 6] = player0.action.value / self.actionNormalizer
        state[statePosition + 7] = ((player0.jumps_left))
        state[statePosition + 8] = ((player0.x / self.positionNormalizer))
        state[statePosition + 9] = ((player0.y / self.positionNormalizer))

        state[statePosition + 10] = 1 if player0.facing else 0
        state[statePosition + 11] = 1 if player0.off_stage else 0
        state[statePosition + 12] = 1 if player0.on_ground else 0
        state[statePosition + 13] = 1 if player0.invulnerable else 0

    def embed_input(self, gamestate: GameState) -> np.ndarray:
        state: np.ndarray = np.zeros((4, 14))
        player0: PlayerState = gamestate.players[self.player_index]

        self.applyPlayerState(player0, state[0, ...], 0)

        player1: PlayerState = gamestate.players[self.opponent_index]
        self.applyPlayerState(player1, state[3, ...], 0)
        statePosition = 0
        state[1, 0] = player0.controller_state.button[melee.Button.BUTTON_A]
        statePosition += 1
        state[1, 1] = player0.controller_state.button[melee.Button.BUTTON_B]
        statePosition += 1
        state[1, 2] = player0.controller_state.button[melee.Button.BUTTON_Y]
        statePosition += 1
        state[1, 3] = player0.controller_state.button[melee.Button.BUTTON_Z]
        statePosition += 1
        state[1, 4] = player0.controller_state.main_stick[1]
        statePosition += 1
        state[1, 5] = player0.controller_state.main_stick[1]
        statePosition += 1
        state[1, 6] = player0.controller_state.c_stick[0]
        statePosition += 1
        state[1, 7] = player0.controller_state.c_stick[1]
        statePosition += 1
        state[1, 8] = player0.controller_state.l_shoulder
        statePosition += 1
        edge = melee.stages.EDGE_GROUND_POSITION[gamestate.stage]
        leftPlatform = melee.stages.left_platform_position(gamestate.stage)
        topPlatform = melee.stages.top_platform_position(gamestate.stage)
        rightPlatform = melee.stages.right_platform_position(gamestate.stage)
        state[1, 9] = edge / self.positionNormalizer
        state[1, 10] = (edge * -1) / self.positionNormalizer
        blastzones: tuple[float, float, float,
                          float] = melee.stages.BLASTZONES[gamestate.stage]
        state[1, 11] = (
            gamestate.distance) / self.positionNormalizer
        state[1, 12] = (player1.character.value - 13) / 4
        state[1, 13] = (player0.character.value - 13) / 4
        # print(statePosition)
        # statePosition += 1
        statePosition = 0
        # # state[0, 63] = (gamestate.projectiles) / self.positionNormalizer
        for projectile in gamestate.projectiles[:2]:
            projectile: Projectile
            if projectile.owner == player0:
                state[2, statePosition] = float(1)
            else:
                state[2, statePosition] = float(0)
            statePosition += 1
            state[2, statePosition] = float(
                projectile.position.x) / self.positionNormalizer
            statePosition += 1
            state[2, statePosition] = float(
                projectile.position.y) / self.positionNormalizer
            statePosition += 1
            state[2, statePosition] = float(
                projectile.speed.x) / 2.5
            statePosition += 1
            state[2, statePosition] = float(
                projectile.speed.y) / 2.5
            statePosition += 1
            state[2, statePosition] = float((projectile.subtype - 5) / 2)
            # self.embedCategory(state, statePosition, , 11)
            statePosition += 1
        return state
