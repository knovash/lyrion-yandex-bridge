package knovash.saclient.server;

import lombok.extern.log4j.Log4j2;
import knovash.saclient.Context;
import knovash.saclient.utils.Parser;
import knovash.saclient.web.PageIndex;
import knovash.saclient.web.PagePlayers;

import java.util.HashMap;
import java.util.Map;

import static knovash.saclient.Main.*;
import static knovash.saclient.web.PagePlayers.*;
import static knovash.saclient.web.Settings.settings;
import static knovash.saclient.web.SettingsButtons.settingsButtons;
import static knovash.saclient.web.SettingsLms.settingsLms;
import static knovash.saclient.web.SettingsTasker.settingsTasker;
import static knovash.saclient.web.SettingsVoice.settingsVoice;

@Log4j2
public class ProcessForm {

    public static void processFormContext(Context context) {
        Map<String, String> bodyMap = Parser.bodyToMap(context.body);
        context.code = 200;
        if (bodyMap.containsKey("action")) {
            String action = bodyMap.get("action");

            log.info("SWITCH CASE action: " + action);
            switch (action) {
                case "settings_save_all":
                    settings.handleSaveAll(context);

                    break;
                case "settings_buttons_save":
                    settingsButtons.handleSaveAll(context);
                    break;
                case "settings_lms_save":
                    settingsLms.handleSaveAll(context);
                    break;
                case "settings_tasker_save":
                    settingsTasker.handleSaveAll(context);
                    break;
                case "settings_voice_save":
                    settingsVoice.handleSaveAll(context);
                    break;
                case statusbar_refresh:
                    PageIndex.refresh((HashMap<String, String>) bodyMap);
                    context.setRedirect("/");
                    break;
                case delay_expire_save:
                    lmsPlayers.delayExpireSave((HashMap<String, String>) bodyMap);
                    context.bodyResponse = PagePlayers.page();
                    break;
                case player_room_set:
                    lmsPlayers.playerRoomSet((HashMap<String, String>) bodyMap);
                    context.setRedirect("/players");
                    break;
                case player_save:
                    lmsPlayers.playerSave((HashMap<String, String>) bodyMap);
                    context.setRedirect("/players");
                    break;
                case player_remove:
                    lmsPlayers.playerRemove((HashMap<String, String>) bodyMap);
                    context.setRedirect("/players");
                    break;
                case lms_save:
                    lmsPlayers.lmsSave((HashMap<String, String>) bodyMap);
                    context.setRedirect("/lms");
                    break;
                default:
                    log.info("ACTION ERROR " + action);
                    break;
            }
        }
    }
}
