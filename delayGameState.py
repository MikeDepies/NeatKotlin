import melee
class DelayGameState:

    def __init__(self, frame_delay : int) -> None:
        self.frame_delay = frame_delay
        self.frames = []
    
    def newFrame(self, game_state : melee.GameState):
        self.frames.insert(0, game_state)
        if len(self.frames) >= self.frame_delay:
            return self.frames.pop()
        else:
            return None