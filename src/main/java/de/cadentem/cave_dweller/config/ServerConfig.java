package de.cadentem.cave_dweller.config;

import de.cadentem.cave_dweller.CaveDweller;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Properties;

public class ServerConfig {
   @ConfigEntry(description = "Minimum time between spawns in seconds")
   public static int RESET_CALM_MIN = 300;
   @ConfigEntry(description = "Maximum time between spawns in seconds")
   public static int RESET_CALM_MAX = 600;
   @ConfigEntry(description = "Spawn cooldown length in seconds")
   public static int RESET_CALM_COOLDOWN = 1200;
   @ConfigEntry(description = "Chance for a spawn cooldown to occur")
   public static double RESET_CALM_COOLDOWN_CHANCE = 0.4;
   @ConfigEntry(description = "Minimum time between noise occurrences in seconds")
   public static int RESET_NOISE_MIN = 240;
   @ConfigEntry(description = "Maximum time between noise occurrences in seconds")
   public static int RESET_NOISE_MAX = 360;
   @ConfigEntry(description = "The spawn chance per tick (once the calm timer is finished)")
   public static double SPAWN_CHANCE_PER_TICK = 0.005;
   @ConfigEntry(description = "Depth at which the Cave Dweller can start to spawn")
   public static int SPAWN_HEIGHT = 40;
   @ConfigEntry(description = "Whether the Cave Dweller can spawn on the surface or not")
   public static boolean ALLOW_SURFACE_SPAWN = false;
   @ConfigEntry(description = "The maximum sky light level the Cave Dweller can spawn at")
   public static int SKY_LIGHT_LEVEL = 8;
   @ConfigEntry(description = "The maximum block light level the Cave Dweller can spawn at")
   public static int BLOCK_LIGHT_LEVEL = 15;
   @ConfigEntry(description = "The distance in blocks at which the Cave Dweller can detect whether a player is looking at it or not")
   public static int SPOTTING_RANGE = 60;
   @ConfigEntry(description = "Time (in seconds) it takes for the Cave Dweller to leave")
   public static int TIME_UNTIL_LEAVE = 300;
   @ConfigEntry(description = "Time (in seconds) it takes for the Cave Dweller to leave once a chase begins")
   public static int TIME_UNTIL_LEAVE_CHASE = 30;
   @ConfigEntry(description = "Depth Strider speed bonus")
   public static double DEPTH_STRIDER_BONUS = 1.5;
   @ConfigEntry(description = "Whether the cave dweller can climb or not")
   public static boolean CAN_CLIMB = true;
   @ConfigEntry(description = "Whether the cave dweller can break down doors or not")
   public static boolean CAN_BREAK_DOOR = true;
   @ConfigEntry(description = "Time (in seconds) it takes the Cave Dweller to break down a door")
   public static int BREAK_DOOR_TIME = 3;
   @ConfigEntry(description = "Whether the Cave Dweller breaks blocks that stand in its way while chasing")
   public static boolean CAN_BREAK_BLOCKS = true;
   @ConfigEntry(description = "Time (in seconds) the Cave Dweller needs to break through blocks in its way")
   public static int BREAK_BLOCK_TIME = 1;
   @ConfigEntry(description = "Maximum block hardness the Cave Dweller can break (stone 1.5, deepslate 3, obsidian 50)")
   public static double BREAK_BLOCK_MAX_HARDNESS = 10.0;
   @ConfigEntry(description = "Chance (0-1) that a new Cave Dweller starts out stalking (creeps closer while unseen, freezes when looked at)")
   public static double STALK_CHANCE = 0.7;
   @ConfigEntry(description = "While stalking, the Cave Dweller occasionally snuffs out torches and lanterns near the player")
   public static boolean SNUFF_LIGHTS = true;
   @ConfigEntry(description = "While stalking, the Cave Dweller occasionally makes the player's vision go dark")
   public static boolean CREEPY_DARKNESS = true;
   @ConfigEntry(description = "Allow the Cave Dweller to follow vanilla riding logic (e.g. boats)")
   public static boolean ALLOW_RIDING = false;
   @ConfigEntry(description = "Whether invisible players can be targets or not")
   public static boolean TARGET_INVISIBLE = true;
   @ConfigEntry(description = "Maximum health")
   public static double MAX_HEALTH = 60.0;
   @ConfigEntry(description = "Attack damage")
   public static double ATTACK_DAMAGE = 6.0;
   @ConfigEntry(description = "Attack speed")
   public static double ATTACK_SPEED = 0.35;
   @ConfigEntry(description = "Movement speed")
   public static double MOVEMENT_SPEED = 0.5;
   @ConfigEntry(description = "Chase stalking: minimum seconds he keeps his distance (staying in sight) before charging. Set both times to 0 for an instant charge")
   public static int CHASE_STALK_MIN_TIME = 20;
   @ConfigEntry(description = "Chase stalking: maximum seconds he keeps his distance before charging")
   public static int CHASE_STALK_MAX_TIME = 60;
   @ConfigEntry(description = "Chase stalking: he backs away if you come closer than this many blocks")
   public static int CHASE_STALK_MIN_DISTANCE = 14;
   @ConfigEntry(description = "Chase stalking: he moves closer if you are further away than this many blocks or out of line of sight")
   public static int CHASE_STALK_MAX_DISTANCE = 28;
   @ConfigEntry(description = "If true he sometimes stalks players on the surface at night (he never attacks in this mode)")
   public static boolean SURFACE_STALKING = true;
   @ConfigEntry(description = "Minimum time in seconds between surface stalking visits")
   public static int SURFACE_STALK_COOLDOWN_MIN = 180;
   @ConfigEntry(description = "Maximum time in seconds between surface stalking visits")
   public static int SURFACE_STALK_COOLDOWN_MAX = 420;
   @ConfigEntry(description = "Chance (0 to 1) that a visit actually happens once the cooldown is over")
   public static double SURFACE_STALK_CHANCE = 0.6;
   @ConfigEntry(description = "How long in seconds a surface stalker stays before leaving")
   public static int SURFACE_STALK_TIME = 180;
   private static final File propertiesFile = new File("config/cave_dweller.properties");

   public static void load() {
      Properties properties = new Properties();
      boolean loadedProperties = false;

      try {
         properties.load(new FileInputStream(propertiesFile));
         loadedProperties = true;
      } catch (FileNotFoundException e) {
         CaveDweller.LOG.info("Our properties file doesn't exist, creating it");

         try {
            boolean ignored = propertiesFile.getParentFile().mkdirs();
            if (!propertiesFile.createNewFile()) {
               CaveDweller.LOG.warn("Our properties file actually exists... What?");
            }

            save();
         } catch (IOException ex) {
            CaveDweller.LOG.error("Couldn't create the properties file");
            CaveDweller.LOG.error(ex.getMessage());
         }
      } catch (IOException e) {
         CaveDweller.LOG.error("Couldn't read the properties file");
         CaveDweller.LOG.error(e.getMessage());
      }

      if (loadedProperties) {
         boolean rewriteConfig = false;

         for (Field field : ServerConfig.class.getDeclaredFields()) {
            ConfigEntry annotation = field.getAnnotation(ConfigEntry.class);
            if (annotation != null) {
               String propertiesValue = properties.getProperty(annotation.name().equals("") ? field.getName() : annotation.name());
               if (propertiesValue == null) {
                  rewriteConfig = true;
               } else {
                  try {
                     if (field.getType() == String.class) {
                        field.set(null, propertiesValue);
                     } else if (field.getType() == boolean.class) {
                        field.setBoolean(null, Boolean.parseBoolean(propertiesValue));
                     } else if (field.getType() == int.class) {
                        field.setInt(null, Integer.parseInt(propertiesValue));
                     } else if (field.getType() == double.class) {
                        field.setDouble(null, Double.parseDouble(propertiesValue));
                     } else {
                        CaveDweller.LOG.warn("Bug: unsupported config type " + field.getType().getSimpleName());
                     }
                  } catch (IllegalAccessException e) {
                     CaveDweller.LOG.warn("Bug: can't modify a config field");
                  } catch (NumberFormatException e) {
                     CaveDweller.LOG.warn("Field " + field.getName() + " in the properties type is not of type " + field.getType().getSimpleName());
                  }
               }
            }
         }

         if (rewriteConfig) {
            CaveDweller.LOG.info("Properties file doesn't contain all fields available, rewriting it");
            save();
         }
      }
   }

   public static void save() {
      Properties properties = new Properties();
      StringBuilder fieldsDescriptions = new StringBuilder();

      for (Field field : ServerConfig.class.getDeclaredFields()) {
         ConfigEntry annotation = field.getAnnotation(ConfigEntry.class);
         if (annotation != null) {
            try {
               String fieldName = annotation.name().equals("") ? field.getName() : annotation.name();
               if (!annotation.description().equals("")) {
                  fieldsDescriptions.append("\n").append(fieldName).append(" - ").append(annotation.description());
               }

               properties.setProperty(fieldName, String.valueOf(field.get(null)));
            } catch (IllegalAccessException e) {
               CaveDweller.LOG.warn("Bug: can't access a config field");
            }
         }
      }

      try {
         String comments = "Mod properties file";
         if (!fieldsDescriptions.isEmpty()) {
            comments = comments + "\nField descriptions:" + fieldsDescriptions;
         }

         properties.store(new FileOutputStream(propertiesFile), comments);
      } catch (FileNotFoundException e) {
         CaveDweller.LOG.error("Couldn't save the properties file because it doesn't exist");
      } catch (IOException e) {
         CaveDweller.LOG.error("Couldn't save the properties file");
         CaveDweller.LOG.error(e.getMessage());
      }
   }
}
