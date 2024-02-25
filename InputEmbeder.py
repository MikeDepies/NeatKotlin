import math
import melee
from melee.gamestate import GameState, PlayerState, Projectile
import numpy as np


class InputEmbeder:
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
                state[0, statePosition + i] = 1
            else:
                state[0, statePosition + i] = 0

    def applyPlatform(self, platform, state, statePosition):
        if (platform[0]):
            state[0, statePosition + 0] = platform[0] / self.positionNormalizer
        if (platform[1]):
            state[0, statePosition + 1] = platform[1] / self.positionNormalizer
        if (platform[2]):
            state[0, statePosition + 2] = platform[2] / self.positionNormalizer
        return statePosition + 3

    def applyPlayerState(self, player0: PlayerState, state: np.ndarray, statePosition):
        state[0, statePosition + 0] = player0.speed_air_x_self / \
            self.positionNormalizer
        state[0, statePosition + 1] = player0.speed_ground_x_self / \
            self.positionNormalizer
        state[0, statePosition + 2] = player0.speed_x_attack / \
            self.positionNormalizer
        state[0, statePosition + 3] = player0.speed_y_attack / \
            self.positionNormalizer
        state[0, statePosition + 4] = player0.speed_y_self / \
            self.positionNormalizer
        state[0, statePosition +
              5] = ((player0.percent / self.positionNormalizer))
        state[0, statePosition + 6] = ((player0.shield_strength / 60))
        state[0, statePosition + 7] = ((player0.stock / 4))
        state[0, statePosition + 8] = (
            (player0.action_frame / self.actionNormalizer))
        state[0, statePosition + 9] = (
            (player0.hitstun_frames_left / self.actionNormalizer))
        self.embedCategory(state, statePosition + 10,
                           player0.character.value, 26)
        self.embedCategory(state, statePosition + 36,
                           player0.action.value, 386)
        state[0, statePosition +
              412] = ((player0.iasa / self.actionNormalizer))
        state[0, statePosition + 413] = (
            (player0.invulnerability_left / self.actionNormalizer))
        state[0, statePosition + 414] = ((player0.jumps_left / 2))
        state[0, statePosition + 415] = ((player0.x / self.positionNormalizer))
        state[0, statePosition + 416] = ((player0.y / self.positionNormalizer))

        rangeForward = self.frame_data.range_forward(
            player0.character, player0.action, player0.action_frame)
        rangeBackward = self.frame_data.range_backward(
            player0.character, player0.action, player0.action_frame)
        if (math.isnan(rangeBackward)):
            rangeBackward = 0
        if (math.isnan(rangeForward)):
            rangeForward = 0
        self.embedCategory(state, statePosition + 417, self.frame_data.attack_state(
            player0.character, player0.action, player0.action_frame).value, 4)
        self.embedCategory(state, statePosition + 421,
                           self.frame_data.hitbox_count(player0.character, player0.action), 6)
        state[0, statePosition + 421] = (
            (self.frame_data.hitbox_count(player0.character, player0.action) / 5))
        state[0, statePosition +
              427] = ((rangeForward / self.positionNormalizer))
        state[0, statePosition +
              428] = ((rangeBackward / self.positionNormalizer))

        state[0, statePosition + 429] = 1 if player0.facing else 0
        state[0, statePosition + 430] = 1 if player0.off_stage else 0
        state[0, statePosition + 431] = 1 if player0.on_ground else 0
        state[0, statePosition + 432] = 1 if self.frame_data.is_attack(
            player0.character, player0.action) else 0
        state[0, statePosition + 433] = 1 if self.frame_data.is_grab(
            player0.character, player0.action) else 0
        state[0, statePosition + 434] = 1 if self.frame_data.is_bmove(
            player0.character, player0.action) else 0
        state[0, statePosition + 435] = 1 if self.frame_data.is_roll(
            player0.character, player0.action) else 0
        return statePosition + 436

    def embed_input(self, gamestate: GameState):
        state: np.ndarray = np.zeros((1, 1105))
        player0: PlayerState = gamestate.players[self.player_index]

        statePosition = self.applyPlayerState(player0, state, 0)

        player1: PlayerState = gamestate.players[self.opponent_index]
        statePosition = self.applyPlayerState(player1, state, statePosition)
        self.embedCategory(state, statePosition, gamestate.stage.value, 26)
        statePosition = statePosition + 26
        edge = melee.stages.EDGE_GROUND_POSITION[gamestate.stage]
        leftPlatform = melee.stages.left_platform_position(gamestate.stage)
        topPlatform = melee.stages.top_platform_position(gamestate.stage)
        rightPlatform = melee.stages.right_platform_position(gamestate.stage)
        state[0, statePosition] = edge / self.positionNormalizer
        state[0, statePosition + 1] = (edge * -1) / self.positionNormalizer
        blastzones: tuple[float, float, float,
                          float] = melee.stages.BLASTZONES[gamestate.stage]
        state[0, statePosition +
              2] = (blastzones[0]) / self.positionNormalizer
        state[0, statePosition +
              3] = (blastzones[1]) / self.positionNormalizer
        state[0, statePosition +
              4] = (blastzones[2]) / self.positionNormalizer
        state[0, statePosition +
              5] = (blastzones[3]) / self.positionNormalizer
        statePosition += 6
        statePosition = self.applyPlatform(
            leftPlatform, state, statePosition)
        statePosition = self.applyPlatform(
            topPlatform, state, statePosition)
        statePosition = self.applyPlatform(
            rightPlatform, state, statePosition)
        state[0, statePosition] = (
            gamestate.distance) / self.positionNormalizer
        statePosition += 1
        # # state[0, 63] = (gamestate.projectiles) / self.positionNormalizer
        for projectile in gamestate.projectiles[:10]:
            projectile: Projectile
            self.embedCategory(state, statePosition, projectile.owner, 4)
            statePosition += 4
            state[0, statePosition] = float(
                projectile.position.x) / self.positionNormalizer
            statePosition += 1
            state[0, statePosition] = float(
                projectile.position.y) / self.positionNormalizer
            statePosition += 1
            state[0, statePosition] = float(
                projectile.speed.x) / self.positionNormalizer
            statePosition += 1
            state[0, statePosition] = float(
                projectile.speed.y) / self.positionNormalizer
            statePosition += 1
            self.embedCategory(state, statePosition, projectile.subtype, 11)
            statePosition += 11
        # non_zero_elements = np.nonzero(state)
        # print("Non-zero state values and their indices:")
        # for index in zip(*non_zero_elements):
        #     print(f"Index: {index}, Value: {state[index]}")
        return [state]