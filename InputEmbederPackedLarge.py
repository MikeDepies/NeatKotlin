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

    def applyPlayerState(self, player0: PlayerState, state: np.ndarray):
        state[0, 0] = player0.speed_air_x_self / 2.5
        state[0, 1] = player0.speed_ground_x_self / 2.5
        state[0, 2] = player0.speed_x_attack / 2.5
        state[0, 3] = player0.speed_y_attack / 2.5
        state[0, 4] = player0.speed_y_self / 2.5
        state[0, 5] = player0.position.x / self.positionNormalizer
        state[0, 6] = player0.position.y / self.positionNormalizer
        state[0, 7] = player0.character.value / 26

        state[1, 0] = player0.ecb.top.x
        state[1, 1] = player0.ecb.top.y
        state[1, 2] = player0.ecb.left.x
        state[1, 3] = player0.ecb.left.y
        state[1, 4] = player0.ecb.right.x
        state[1, 5] = player0.ecb.right.y
        state[1, 6] = player0.ecb.bottom.x
        state[1, 7] = player0.ecb.bottom.y

        state[2, 0] = ((player0.percent / 100))
        state[2, 1] = player0.stock / 4
        state[2, 2] = player0.action.value / self.actionNormalizer
        state[2, 3] = player0.action_frame / \
            self.frame_data.frame_count(player0.character, player0.action)
        state[2, 4] = player0.invulnerability_left / 60
        state[2, 5] = player0.hitstun_frames_left / 60
        state[2, 6] = player0.hitlag_left / 60
        state[2, 7] = player0.shield_strength / 60

        state[3, 0] = 0
        if player0.facing:
            state[3, 0] = 1
        state[3, 1] = 0
        if player0.invulnerable:
            state[3, 1] = 1
        state[3, 2] = 0
        if player0.off_stage:
            state[3, 2] = 1
        state[3, 3] = 0
        if (player0.on_ground):
            state[3, 3] = 1
        state[3, 4] = 0
        if (self.frame_data.is_shield(player0.action)):
            state[3, 4] = 1
        state[3, 5] = 0
        if (self.frame_data.is_roll(player0.character, player0.action)):
            state[3, 5] = 1
        state[3, 6] = 0
        if (self.frame_data.is_grab(player0.character, player0.action)):
            state[3, 6] = 1
        state[3, 7] = player0.jumps_left / \
            self.frame_data.max_jumps(player0.character)

        state[4, 0] = 0
        if self.frame_data.is_attack(player0.character, player0.action):
            state[4, 0] = 1
        state[4, 1] = 0
        if self.frame_data.is_bmove(player0.character, player0.action):
            state[4, 1] = 1
        state[4, 2] = 0
        if self.frame_data.is_grab(player0.character, player0.action):
            state[4, 2] = 1
        state[4, 3] = 0
        if self.frame_data.attack_state(player0.character, player0.action, player0.action_frame) == melee.AttackState.NOT_ATTACKING:
            state[4, 3] = 1
        state[4, 4] = 0
        if self.frame_data.attack_state(player0.character, player0.action, player0.action_frame) == melee.AttackState.WINDUP:
            state[4, 4] = 1
        state[4, 5] = 0
        if self.frame_data.attack_state(player0.character, player0.action, player0.action_frame) == melee.AttackState.ATTACKING:
            state[4, 5] = 1
        state[4, 6] = 0
        if self.frame_data.attack_state(player0.character, player0.action, player0.action_frame) == melee.AttackState.COOLDOWN:
            state[4, 6] = 1
        state[4, 7] = player0.jumps_left / self.frame_data.max_jumps(player0)

    def embed_input(self, gamestate: GameState) -> 'list[np.ndarray]':
        state: np.ndarray = np.zeros((5, 8))
        player0: PlayerState = gamestate.players[self.player_index]

        self.applyPlayerState(player0, state)

        state_2: np.ndarray = np.zeros((5, 8))
        player1: PlayerState = gamestate.players[self.opponent_index]
        self.applyPlayerState(player1, state_2)

        state_controller: np.ndarray = np.zeros((1, 9))
        state_controller[0, 0] = player0.controller_state.button[melee.Button.BUTTON_A]
        state_controller[0, 1] = player0.controller_state.button[melee.Button.BUTTON_B]
        state_controller[0, 2] = player0.controller_state.button[melee.Button.BUTTON_Y]
        state_controller[0, 3] = player0.controller_state.button[melee.Button.BUTTON_Z]
        state_controller[0, 4] = player0.controller_state.main_stick[0]
        state_controller[0, 5] = player0.controller_state.main_stick[1]
        state_controller[0, 6] = player0.controller_state.c_stick[0]
        state_controller[0, 7] = player0.controller_state.c_stick[1]
        state_controller[0, 8] = player0.controller_state.l_shoulder
        # print(str(player0.controller_state.main_stick))
        # print(str(player0.controller_state))
        state_projectile: np.ndarray = np.zeros((8, 8))
        for index, projectile in enumerate(gamestate.projectiles[:8]):
            projectile: Projectile
            if projectile.owner == player0:
                state_projectile[index, 0] = float(1)
            else:
                state_projectile[index, 0] = float(-1)
            state_projectile[index, 1] = float(
                projectile.position.x) / self.positionNormalizer

            state_projectile[index, 2] = float(
                projectile.position.y) / self.positionNormalizer

            state_projectile[index, 3] = float(
                projectile.speed.x) / 2.5

            state_projectile[index, 4] = float(
                projectile.speed.y) / 2.5
            state_projectile[index, 4] = float(
                projectile.frame) / 60

            state_projectile[index, 6] = float((projectile.subtype - 5) / 2)
            state_projectile[index, 7] = float((projectile.type.value) / 156)
            # self.embedCategory(state, statePosition, , 11)

        state_stage: np.ndarray = np.zeros((1, 16))
        edge = melee.stages.EDGE_GROUND_POSITION[gamestate.stage]
        leftPlatform = melee.stages.left_platform_position(gamestate.stage)
        topPlatform = melee.stages.top_platform_position(gamestate.stage)
        rightPlatform = melee.stages.right_platform_position(gamestate.stage)
        state_stage[0, 0] = (
            gamestate.distance) / self.positionNormalizer
        state_stage[0, 1] = edge / self.positionNormalizer
        state_stage[0, 2] = (edge * -1) / self.positionNormalizer
        blastzones: tuple[float, float, float,
                          float] = melee.stages.BLASTZONES[gamestate.stage]
        state_stage[0, 3] = blastzones[0] / self.positionNormalizer
        state_stage[0, 4] = blastzones[1] / self.positionNormalizer
        state_stage[0, 5] = blastzones[2] / self.positionNormalizer
        state_stage[0, 6] = blastzones[3] / self.positionNormalizer
        
        self.applyPlatform(leftPlatform, state_stage[0, ...], 7)
        self.applyPlatform(topPlatform, state_stage[0, ...], 10)
        self.applyPlatform(rightPlatform, state_stage[0, ...], 13)
        return [state, state_2, state_projectile, state_controller, state_stage]
