import math
import melee
from melee.gamestate import GameState, PlayerState, Projectile
import numpy as np


class InputEmbederPacked2:
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

    def applyPlayerState(self, player0: PlayerState, state: np.ndarray, statePosition : int):
        state[statePosition + 0] = player0.speed_air_x_self / \
            self.positionNormalizer
        state[statePosition + 1] = player0.speed_ground_x_self / \
            self.positionNormalizer
        state[statePosition + 2] = player0.speed_x_attack / \
            self.positionNormalizer
        state[statePosition + 3] = player0.speed_y_attack / \
            self.positionNormalizer
        state[statePosition + 4] = player0.speed_y_self / \
            self.positionNormalizer
        state[statePosition +
              5] = ((player0.percent / self.positionNormalizer))
        state[statePosition + 6] = ((player0.shield_strength / 60))
        state[statePosition + 7] = ((player0.stock / 4))
        state[statePosition + 8] = (
            (player0.action_frame / self.actionNormalizer))
        state[statePosition + 9] = (
            (player0.hitstun_frames_left / self.actionNormalizer))
        state[statePosition + 10] = player0.character.value / 26
        
        state[statePosition + 11] = player0.action.value
        state[statePosition +
              12] = ((player0.iasa / self.actionNormalizer))
        state[statePosition + 13] = (
            (player0.invulnerability_left / self.actionNormalizer))
        state[statePosition + 14] = ((player0.jumps_left / 2))
        state[statePosition + 15] = ((player0.x / self.positionNormalizer))
        state[statePosition + 16] = ((player0.y / self.positionNormalizer))

        rangeForward = self.frame_data.range_forward(
            player0.character, player0.action, player0.action_frame)
        rangeBackward = self.frame_data.range_backward(
            player0.character, player0.action, player0.action_frame)
        if (math.isnan(rangeBackward)):
            rangeBackward = 0
        if (math.isnan(rangeForward)):
            rangeForward = 0

        state[statePosition + 17] = self.frame_data.attack_state(
            player0.character, player0.action, player0.action_frame).value /4
        state[statePosition + 18] = self.frame_data.hitbox_count(player0.character, player0.action) /6
        
        state[statePosition +
              19] = ((rangeForward / self.positionNormalizer))
        state[statePosition +
              20] = ((rangeBackward / self.positionNormalizer))

        state[statePosition + 21] = 1 if player0.facing else 0
        state[statePosition + 22] = 1 if player0.off_stage else 0
        state[statePosition + 23] = 1 if player0.on_ground else 0
        state[statePosition + 24] = 1 if self.frame_data.is_attack(
            player0.character, player0.action) else 0
        state[statePosition + 25] = 1 if self.frame_data.is_grab(
            player0.character, player0.action) else 0
        state[statePosition + 26] = 1 if self.frame_data.is_bmove(
            player0.character, player0.action) else 0
        state[statePosition + 27] = 1 if self.frame_data.is_roll(
            player0.character, player0.action) else 0
        return statePosition + 28

    def embed_input(self, gamestate: GameState) -> np.ndarray:
        state: np.ndarray = np.zeros((4, 30))
        player0: PlayerState = gamestate.players[self.player_index]
        
        statePosition = self.applyPlayerState(player0, state[0,...], 0)

        player1: PlayerState = gamestate.players[self.opponent_index]
        statePosition = self.applyPlayerState(player1, state[3,...], 0)
        statePosition = 0
        state[1, statePosition] = player0.controller_state.button[melee.Button.BUTTON_A]
        statePosition +=1
        state[1, statePosition] = player0.controller_state.button[melee.Button.BUTTON_B]
        statePosition +=1
        state[1, statePosition] = player0.controller_state.button[melee.Button.BUTTON_Y]
        statePosition +=1
        state[1, statePosition] = player0.controller_state.button[melee.Button.BUTTON_Z]
        statePosition +=1
        state[1, statePosition] = player0.controller_state.main_stick[1]
        statePosition +=1
        state[1, statePosition] = player0.controller_state.main_stick[1]
        statePosition +=1
        state[1, statePosition] = player0.controller_state.c_stick[0]
        statePosition +=1
        state[1, statePosition] = player0.controller_state.c_stick[1]
        statePosition +=1
        state[1, statePosition] = player0.controller_state.l_shoulder
        statePosition +=1
        state[1, statePosition] = gamestate.stage.value / 26
        statePosition = statePosition + 1
        edge = melee.stages.EDGE_GROUND_POSITION[gamestate.stage]
        leftPlatform = melee.stages.left_platform_position(gamestate.stage)
        topPlatform = melee.stages.top_platform_position(gamestate.stage)
        rightPlatform = melee.stages.right_platform_position(gamestate.stage)
        state[1, statePosition] = edge / self.positionNormalizer
        state[1, statePosition + 1] = (edge * -1) / self.positionNormalizer
        blastzones: tuple[float, float, float,
                          float] = melee.stages.BLASTZONES[gamestate.stage]
        state[1, statePosition +
              2] = (blastzones[0]) / self.positionNormalizer
        state[1, statePosition +
              3] = (blastzones[1]) / self.positionNormalizer
        state[1, statePosition +
              4] = (blastzones[2]) / self.positionNormalizer
        state[1, statePosition +
              5] = (blastzones[3]) / self.positionNormalizer
        statePosition += 6
        statePosition = self.applyPlatform(
            leftPlatform, state[1,...], statePosition)
        statePosition = self.applyPlatform(
            topPlatform, state[1,...], statePosition)
        statePosition = self.applyPlatform(
            rightPlatform, state[1,...], statePosition)
        state[1, statePosition] = (
            gamestate.distance) / self.positionNormalizer
        # print(statePosition)
        statePosition += 1
        statePosition = 0
        # # state[0, 63] = (gamestate.projectiles) / self.positionNormalizer
        for projectile in gamestate.projectiles[:5]:
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
                projectile.speed.x) / self.positionNormalizer
            statePosition += 1
            state[2, statePosition] = float(
                projectile.speed.y) / self.positionNormalizer
            statePosition += 1
            state[2, statePosition] = float(projectile.subtype / 11)
            # self.embedCategory(state, statePosition, , 11)
            statePosition += 1
        return state
