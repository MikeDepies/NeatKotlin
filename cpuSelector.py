import melee
import math
def choose_character(character, gamestate, controller, cpu_level=0, costume=2, swag=False, start=False):
        """Choose a character from the character select menu

        Args:
            character (melee.Character): The character you want to pick
            gamestate (gamestate.GameState): The current gamestate
            controller (controller.Controller): The controller object to press buttons on
            cpu_level (int): What CPU level to set this to. 0 for human/bot controlled.
            costume (int): The costume index to choose
            swag (bool): Pick random until you get the character
            start (bool): Automatically start the match when it's ready

        Note:
            Intended to be called each frame while in the character select menu

        Note:
            All controller cursors must be above the character level for this
            to work. The match won't start otherwise.
        """
        cpu_token_x_delta = [
            0,
            0.746401,
            1.996401,
            3.483204,
            4.478405,
            5.971207,
            6.966408,
            8.459207,
            9.454408
        ]
        # Figure out where the character is on the select screen
        # NOTE: This assumes you have all characters unlocked
        # Positions will be totally wrong if something is not unlocked
        controlling_port = controller.port
        if controlling_port not in gamestate.players:
            controller.release_all()
            return

        ai_state = gamestate.players[controlling_port]

        # Discover who is the opponent
        opponent_state = None
        for i, player in gamestate.players.items():
            # TODO For now, just assume they're the first controller port that isn't us
            if i != controlling_port:
                opponent_state = player
                break

        cursor_x, cursor_y = ai_state.cursor_x, ai_state.cursor_y
        coin_down = ai_state.coin_down
        character_selected = ai_state.character

        isSlippiCSS = False
        if gamestate.menu_state == melee.Menu.SLIPPI_ONLINE_CSS:
            cursor_x, cursor_y = gamestate.players[1].cursor_x, gamestate.players[1].cursor_y
            isSlippiCSS = True
            character_selected = gamestate.players[1].character
        if isSlippiCSS:
            swag = True

        row = melee.from_internal(character) // 9
        column = melee.from_internal(character) % 9
        #The random slot pushes the bottom row over a slot, so compensate for that
        if row == 2:
            column = column+1
        #re-order rows so the math is simpler
        row = 2-row

        #Go to the random character
        if swag:
            row = 0
            column = 0

        #Height starts at 1, plus half a box height, plus the number of rows
        target_y = 1 + 3.5 + (row * 7.0)
        #Starts at -32.5, plus half a box width, plus the number of columns
        #NOTE: Technically, each column isn't exactly the same width, but it's close enough
        target_x = -32.5 + 3.5 + (column * 7.0)
        #Wiggle room in positioning character
        wiggleroom = 1.5

        # Set our CPU level correctly
        if character_selected == character and (coin_down or cursor_y<0) and cpu_level>0 \
            and (cpu_level != ai_state.cpu_level) or ai_state.is_holding_cpu_slider:
            # Is our controller type correct?
            cpu_selected = ai_state.controller_status == melee.ControllerStatus.CONTROLLER_CPU
            if cpu_selected != (cpu_level > 0):
                wiggleroom = 1
                target_y = -2.2
                target_x = -32.2 + (15.82 * (controlling_port-1))

                controller.release_button(melee.Button.BUTTON_A)
                #Move up if we're too low
                if cursor_y < target_y - wiggleroom:
                    controller.tilt_analog(melee.Button.BUTTON_MAIN, .5, 1)
                    return
                #Move down if we're too high
                if cursor_y > target_y + wiggleroom:
                    controller.tilt_analog(melee.Button.BUTTON_MAIN, .5, 0)
                    return
                #Move right if we're too left
                if cursor_x < target_x - wiggleroom:
                    controller.tilt_analog(melee.Button.BUTTON_MAIN, 1, .5)
                    return
                #Move left if we're too right
                if cursor_x > target_x + wiggleroom:
                    controller.tilt_analog(melee.Button.BUTTON_MAIN, 0, .5)
                    return

                if gamestate.frame % 2 == 0:
                    controller.press_button(melee.Button.BUTTON_A)
                else:
                    controller.release_all()
                return
            # Select the right CPU level on the slider
            if ai_state.is_holding_cpu_slider:
                if ai_state.cpu_level > cpu_level:
                    controller.tilt_analog(melee.Button.BUTTON_MAIN, .35, .5)
                    return
                if ai_state.cpu_level < cpu_level:
                    controller.tilt_analog(melee.Button.BUTTON_MAIN, .65, .5)
                    return
                if ai_state.cpu_level == cpu_level:
                    print("level: " + str(cpu_level) + " cursor: " + str(cursor_x))
                    if gamestate.frame % 2 == 0:
                        controller.press_button(melee.Button.BUTTON_A)
                    else:
                        controller.release_all()
                return
            # Move over to and pick up the CPU slider
            if ai_state.cpu_level != cpu_level:
                wiggleroom = 1
                target_y = -15.12
                target_x = -30.9 + (15.4 * (controlling_port-1)) + cpu_token_x_delta[ai_state.cpu_level -1]
                #Move up if we're too low
                if cursor_y < target_y - wiggleroom:
                    controller.tilt_analog(melee.Button.BUTTON_MAIN, .5, .8)
                    return
                #Move down if we're too high
                if cursor_y > target_y + wiggleroom:
                    controller.tilt_analog(melee.Button.BUTTON_MAIN, .5, .2)
                    return
                #Move right if we're too left
                if cursor_x < target_x - wiggleroom:
                    controller.tilt_analog(melee.Button.BUTTON_MAIN, .8, .5)
                    return
                #Move left if we're too right
                if cursor_x > target_x + wiggleroom:
                    controller.tilt_analog(melee.Button.BUTTON_MAIN, .2, .5)
                    return
                if gamestate.frame % 2 == 0:
                    controller.press_button(melee.Button.BUTTON_A)
                else:
                    controller.release_all()
                return
            return

        # We are already set, so let's taunt our opponent
        if character_selected == character and swag and not start:
            delta_x = 3 * math.cos(gamestate.frame / 1.5)
            delta_y = 3 * math.sin(gamestate.frame / 1.5)

            target_x = opponent_state.cursor_x + delta_x
            target_y = opponent_state.cursor_y + delta_y

            diff_x = abs(target_x - cursor_x)
            diff_y = abs(target_y - cursor_y)
            larger_magnitude = max(diff_x, diff_y)

            # Scale down values to between 0 and 1
            x = diff_x / larger_magnitude
            y = diff_y / larger_magnitude

            # Now scale down to be between .5 and 1
            if cursor_x < target_x:
                x = (x/2) + 0.5
            else:
                x = 0.5 - (x/2)
            if cursor_y < target_y:
                y = (y/2) + 0.5
            else:
                y = 0.5 - (y/2)
            controller.tilt_analog(melee.Button.BUTTON_MAIN, x, y)
            return

        if character_selected == character and swag and isSlippiCSS:
            if gamestate.frame % 2 == 0:
                controller.release_all()
                return
            if costume == ai_state.costume:
                controller.press_button(melee.Button.BUTTON_START)
            else:
                controller.press_button(melee.Button.BUTTON_Y)
            return

        #We want to get to a state where the cursor is NOT over the character,
        # but it's selected. Thus ensuring the token is on the character
        isOverCharacter = abs(cursor_x - target_x) < wiggleroom and \
            abs(cursor_y - target_y) < wiggleroom

        #Don't hold down on B, since we'll quit the menu if we do
        if controller.prev.button[melee.Button.BUTTON_B] == True:
            controller.release_button(melee.Button.BUTTON_B)
            return

        #If character is selected, and we're in of the area, and coin is down, then we're good
        if (character_selected == character) and coin_down:
            if gamestate.frame % 2 == 0:
                controller.release_all()
                return
            if start and (gamestate.ready_to_start == 0):
                controller.press_button(melee.Button.BUTTON_START)
                return
            else:
                controller.release_all()
                return

        #release start in addition to anything else
        controller.release_button(melee.Button.BUTTON_START)

        #If we're in the right area, select the character
        if isOverCharacter:
            #If we're over the character, but it isn't selected,
            #   then the coin must be somewhere else.
            #   Press B to reclaim the coin

            controller.tilt_analog(melee.Button.BUTTON_MAIN, .5, .5)

            # The slippi menu doesn't have a coin down. We can make-do
            if isSlippiCSS and (character_selected != character):
                if gamestate.frame % 5 == 0:
                    controller.press_button(melee.Button.BUTTON_B)
                    controller.release_button(melee.Button.BUTTON_A)
                    return
                else:
                    controller.press_button(melee.Button.BUTTON_A)
                    controller.release_button(melee.Button.BUTTON_B)
                    return

            if (character_selected != character) and coin_down:
                controller.press_button(melee.Button.BUTTON_B)
                controller.release_button(melee.Button.BUTTON_A)
                return
            #Press A to select our character
            else:
                if controller.prev.button[melee.Button.BUTTON_A] == False:
                    controller.press_button(melee.Button.BUTTON_A)
                    return
                else:
                    controller.release_button(melee.Button.BUTTON_A)
                    return
        else:
            #Move in
            controller.release_button(melee.Button.BUTTON_A)
            #Move up if we're too low
            if cursor_y < target_y - wiggleroom:
                controller.tilt_analog(melee.Button.BUTTON_MAIN, .5, 1)
                return
            #Move down if we're too high
            if cursor_y > target_y + wiggleroom:
                controller.tilt_analog(melee.Button.BUTTON_MAIN, .5, 0)
                return
            #Move right if we're too left
            if cursor_x < target_x - wiggleroom:
                controller.tilt_analog(melee.Button.BUTTON_MAIN, 1, .5)
                return
            #Move left if we're too right
            if cursor_x > target_x + wiggleroom:
                controller.tilt_analog(melee.Button.BUTTON_MAIN, 0, .5)
                return
        controller.release_all()

def menu_helper_simple(gamestate,
                            controller,
                            character_selected,
                            stage_selected,
                            connect_code="",
                            cpu_level=0,
                            costume=0,
                            autostart=False,
                            swag=False):
    """Siplified menu helper function to get you through the menus and into a game

    Does everything for you but play the game. Gets you to the right menu screen, picks
    your character, chooses the stage, enters connect codes, etc...

    Args:
        gamestate (gamestate.GameState): The current GameState for this frame
        controller (controller.Controller): A Controller object that the bot will press buttons on
        character_selected (enums.Character): The character your bot will play as
        stage_selected (enums.Stage): The stage your bot will choose to play on
        connect_code (str): The connect code to direct match with. Leave blank for VS mode.
        cpu_level (int): What CPU level to set this to. 0 for human/bot controlled.
        costume (int): Costume index chosen
        autostart (bool): Automatically start the game when it's ready.
            Useful for BotvBot matches where no human is there to start it.
        swag (bool): What it sounds like
    """

    # If we're at the character select screen, choose our character
    if gamestate.menu_state in [melee.Menu.CHARACTER_SELECT, melee.Menu.SLIPPI_ONLINE_CSS]:
        if gamestate.submenu == melee.SubMenu.NAME_ENTRY_SUBMENU:
            melee.MenuHelper.name_tag_index = melee.MenuHelper.enter_direct_code(gamestate=gamestate,
                                                        controller=controller,
                                                        connect_code=connect_code,
                                                        index=melee.MenuHelper.name_tag_index)
        else:
            choose_character(character=character_selected,
                                        gamestate=gamestate,
                                        controller=controller,
                                        cpu_level=cpu_level,
                                        costume=costume,
                                        swag=swag,
                                        start=autostart)
    # If we're at the postgame scores screen, spam START
    elif gamestate.menu_state == melee.Menu.POSTGAME_SCORES:
        melee.MenuHelper.skip_postgame(controller=controller)
    # If we're at the stage select screen, choose a stage
    elif gamestate.menu_state == melee.Menu.STAGE_SELECT:
        melee.MenuHelper.choose_stage(stage=stage_selected,
                                gamestate=gamestate,
                                controller=controller)
    elif gamestate.menu_state == melee.Menu.MAIN_MENU:
        if connect_code:
            melee.MenuHelper.choose_direct_online(gamestate=gamestate, controller=controller)
        else:
            melee.MenuHelper.choose_versus_mode(gamestate=gamestate, controller=controller)