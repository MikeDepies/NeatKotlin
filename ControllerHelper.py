import math
from typing import Dict

import melee
from numpy import ndarray

from melee.enums import Button

from ComputableNetwork import ComputableNetwork

class ControllerHelper:
    def processMessage(self, message: Dict, controller: melee.Controller):

        if (message["a"] == True):
            controller.press_button(Button.BUTTON_A)
        else:
            controller.release_button(Button.BUTTON_A)
        if (message["b"] == True):
            controller.press_button(Button.BUTTON_B)
        else:
            controller.release_button(Button.BUTTON_B)
        if (message["y"] == True):
            controller.press_button(Button.BUTTON_Y)
        else:
            controller.release_button(Button.BUTTON_Y)

        if (message["z"] == True):
            controller.press_button(Button.BUTTON_Z)
        else:
            controller.release_button(Button.BUTTON_Z)

        controller.tilt_analog(
            Button.BUTTON_MAIN, message["mainStickX"], message["mainStickY"])
        controller.tilt_analog(
            Button.BUTTON_C, message["cStickX"], message["cStickY"])
        controller.press_shoulder(Button.BUTTON_L, message["leftShoulder"])
        if (message["leftShoulder"] >= .9):
            controller.press_button(Button.BUTTON_L)
        else:
            controller.release_button(Button.BUTTON_L)
        # controller.press_shoulder(Button.BUTTON_R, message["rightShoulder"])

        # controller.flush()
    
    def clamp(self, value : float, min_value : float = -4, max_value: float = 4):
        return max(min_value, min(max_value, value))

    def process(self, network : ComputableNetwork, controller : melee.Controller, state : ndarray):
        network.input(state)
        network.compute()
        output1 = network.output()
        outputUnActivated1 = network.outputUnActivated()
        # c_stick_angle = self.clamp(outputUnActivated1[0, 6])
        # main_stick_angle = self.clamp(outputUnActivated1[0, 4])
        main_stick_x = output1[0, 4] #(self.clamp(outputUnActivated1[0,4], -3, 3) / 6) + .5
        main_stick_y = output1[0, 5] #(self.clamp(outputUnActivated1[0,5], -3, 3) / 6) + .5
        c_stick_x = output1[0, 6] #(self.clamp(outputUnActivated1[0,6], -3, 3) / 6) + .5
        c_stick_y = output1[0, 7] #(self.clamp(outputUnActivated1[0,7], -3, 3) / 6) + .5
        self.processMessage({
            "a": output1[0, 0] > .5,
            "b": output1[0, 1] > .5,
            "y": output1[0, 2] > .5,
            "z": output1[0, 3] > .5,
            # "mainStickX": (((math.cos(main_stick_angle  * math.pi)  * output1[0, 5]) + 1) / 2),
            # "mainStickY": (((math.sin(main_stick_angle * math.pi) * output1[0, 5]) + 1) / 2),
            # "cStickX": (((math.cos(c_stick_angle * math.pi)* output1[0, 7]) + 1) / 2),
            # "cStickY": (((math.sin(c_stick_angle * math.pi) * output1[0, 7]) + 1) / 2) ,
            "mainStickX": main_stick_x,
            "mainStickY": main_stick_y,
            "cStickX": c_stick_x,
            "cStickY": c_stick_y,
            "leftShoulder": max(output1[0, 8]-.5, 0) *2,
        }, controller)