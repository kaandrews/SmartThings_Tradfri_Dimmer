/**
 *  Tradfri Dimmer v0.2
 *
 *  Copyright 2017 Kristian Andrews
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  In this handler the following actions are recognised
 *    turning clockwise slowly increased the level, the amount is determined by the time turning
 *    turning clockwise fast increased the level by 50
 *    turning anticlockwise slowly decreases the level, the amount is determined by the time turning
 *    turning anticlockwise fast decreases the level by 50
 *
 *  There are several reported actions that I can't decipher
 *    
 *  When the level reaches 0 the switch is turned off, any level above 0 the switch is on
 *  
 *  Can be used to control lights by the use of a SmartApp such as Synchronised Dimming
 *
 *  To do:
 *  1) Debug why the light flashes when I turn off, maybe I don't need to report events for the ones that are set or the commands should be reversed?
 *  e.g. when Off is set from the app GUI do I need to send an Off event as well?
 *  2) Update so that a fast turn clockwise/anticlockwise can turn the lights on and off but maintain the set level
 */
metadata {
	definition (name: "Tradfri Dimmer v0.2", namespace: "andrews.k", author: "Kristian Andrews") {
		capability "Sensor"
		capability "Switch"
		capability "Switch Level"
        capability "Configuration"

		fingerprint endpointId: "01", profileId: "0104", deviceId: "0810", deviceVersion: "02", inClusters: "0000, 0001, 0003, 0009, 0B05, 1000", outClusters: "0003, 0004, 0006, 0008, 0019, 1000"
		fingerprint endpointId: "01", profileId: "C05E", deviceId: "0810", deviceVersion: "02", inClusters: "0000, 0001, 0003, 0009, 0B05, 1000", outClusters: "0003, 0004, 0006, 0008, 0019, 1000"
	}


	simulator {
		// TODO: define status and reply messages here
	}
    
    // UI tile definitions
    tiles(scale: 2) {
        multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
            }
            tileAttribute ("device.level", key: "SLIDER_CONTROL") {
                attributeState "level", action:"switch level.setLevel"
            }
        }

        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main(["switch"])
        details(["switch", "refresh"])
    }
    
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
	// TODO: handle 'switch' attribute
	// TODO: handle 'level' attribute
	def name = null
    def value = null
    def name2 = null
    def value2 = null
    
    // Check if the stored variables are initialised
    if (state.level == null) {
    	state.level = 100
    }
    
    if (state.OnOff == null) {
    	state.OnOff = 1
    }
    
    if (state.start == null) {
    	state.start = now()
    }
    
    if (state.clockwise == null) {
    	state.clockwise = 0
    }
    
    if (description?.startsWith("catchall:")) {
    	//def descMap = zigbee.parseDescriptionAsMap(description)
        
        log.debug "Catch all: $description"
        log.debug zigbee.parseDescriptionAsMap(description)
        
        // Responds to Zigbee events sent by the dimmer
        // Probably not the most elegant solution
        if (description.endsWith("00 0000 05 00 00C3")) {
        
        	// Start Turn clockwise
            log.debug "cw"
            
            // Store when the turning started and the direction
            state.start = now()
            state.clockwise = 1
        } else if (description.endsWith("00 0000 01 00 01C3")) {
        	// Start turn anticlockwise
            log.debug "acw"
            
            // Store when the turning started and the direction
            state.start = now()
            state.clockwise = -1
        } else if (description.endsWith("00 0000 07 00 ")) {
        	// Stop turning
            log.debug "Stop"
            
            // Calculate duration of turn
            def turnTime = now() - state.start
            //log.debug "Turn ms: $turnTime"
            
            // If the turn is too long ignore it, it probably came from a missed event
            if (turnTime > 2500) {
            	turnTime = 0
            }
            
            // How much change to the level
            // 2000 ms = full up or down
            int change = turnTime / 20
            
            //log.debug change
            
            if (state.clockwise == 1) {
            	// If clockwise, increase the level and turn on
            	
                // Update the stored state
                state.level = state.level + change
                
                // If above 100 reset to 100
                if (state.level > 100){
                	state.level = 100
                }
                
                // Create the ST event details
                name = "level"
                value = state.level
                
                // Turn on switch if off
                if (state.OnOff == 0){
                	// Create the ST event details
                	name2 = "switch"
                	value2 = "on"
                    
                    // Update the stored state
                    state.OnOff = 1
                }
                
                // Set state to not turning
                state.clockwise = 0
            } else if (state.clockwise == -1){
            	// If anticlockwise, decrease the level
                
                // Reduce the stored level
                state.level = state.level - change
                
                // If below 0 reset to 0
                if (state.level < 0){
                	// Make sure it doesn't go below 0
                	state.level = 0
                }
                
                // Create the ST event details
                name = "level"
                value = state.level
                
                // If level = 0 then turn off switch
                if (state.level == 0) {
                	// Update stored state
                	state.OnOff = 0
                    
                    // Create ST event details
                    name2 = "switch"
                    value2 = "off"
                }
                
                // Set state to not turning
                state.clockwise = 0
            }
        } else if (description.endsWith("00 0000 04 00 000100")) {
        	// Fast turn anti-clockwise
            log.debug "fast acw"
            
            // turn down level by 50
            state.level = state.level - 50
            
            // If below 0 reset to 0
            if (state.level < 0){
            	state.level = 0
            }
            
            // Create ST event details
            name = "level"
            value = state.level
            
            // if reached 0 turn off switch
            if (state.level == 0) {
            	if (state.OnOff == 1) {
	            	state.OnOff = 0
    	            name2 = "switch"
        	        value2 = "off"
                }
            }
        } else if (description.endsWith("00 0000 04 00 FF0100")) {
        	// Fast turn clockwise
            log.debug "fast cw"
            
            // turn up level by 50
            state.level = state.level + 50
            
            // make sure it doesn't go above 100
            if (state.level > 100) {
            	state.level = 100
            }
            
            name = "level"
            value = state.level
            
            if (state.OnOff == 0) {
            	state.OnOff = 1
            	name2 = "switch"
            	value2 = "on"
            }
        } else if (description.endsWith("00 0000 07 00 ")) {
        	// Unknown reply - appears after most turns
            log.debug "Unknown catchall 1"
        } else {
            log.debug "Other Catch all: '$description'"
            // The output I have seen so far from the dimmer
            // catchall: 0104 0008 01 01 0100 00 D035 01 00 0000 04 00 000100 - fast turn anti-clockwise
            // catchall: 0104 0008 01 01 0140 00 D035 01 00 0000 04 00 000100 - different type of fast turn anti-clockwise?
            // catchall: 0104 0008 01 01 0100 00 D035 01 00 0000 04 00 FF0100 - fast turn clockwise
            // catchall: 0104 0008 01 01 0140 00 D035 01 00 0000 04 00 FF0100 - different type of fast turn clockwise?
            
            //def descMap = zigbee.parseDescriptionAsMap(description)
        	//log.debug zigbee.parseDescriptionAsMap(description)
        }
    } else {
    	log.debug "Not catch all: $description"
    }
    
    // createEvent returns a Map that defines an Event
    def result = createEvent(name: name, value: value)
    def result2 = createEvent(name: name2, value: value2)
    
    // If there is any result log it
    if (name != null) {
		log.debug "Result 1: ${result?.descriptionText}"
    }
    if (name2 != null) {
    	log.debug "Result 2: ${result2?.descriptionText}"
	}
    
    // returning the Event definition map creates an Event
    // in the SmartThings platform, and propagates it to
    // SmartApps subscribed to the device events.
    return [result, result2]
}

// handle commands
def on() {
	log.debug "Executing 'on'"
	// Handles 'on' commands from the SmartThings app or others
    
    // if switched off, turn on and set level to 50
    state.level = 50
    state.OnOff = 1
    
    // Send switch on and level events
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "level", value: 50)
}

def off() {
	log.debug "Executing 'off'"
	// Handles 'off' commands from the SmartThings app or others
    
    // Update the stored state
    state.level = 0 // Set level to 0
    state.OnOff = 0 // Set to off
    
    // Send events to report set level and switch off
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "level", value: 0)
}

def setLevel(int lev) {
	log.debug "Executing 'setLevel' $lev"
	// Handles 'setLevel' commands from the SmartThings app or others
    
    // If the level has changed
    if (lev != state.level) {
        if (lev == 0) {
        	// If 0 switch off
        	
            // Update the stored state
            state.level = lev
            state.OnOff = 0
            
            // Send events for the updates
            sendEvent(name: "switch", value: "off")
            sendEvent(name: "level", value: 0)
        } else {
        	// Set the stored level
            state.level = lev
            
            // If above 100 set to 100
            if (state.level > 100) {
            	state.level = 100
            }
            
            // If the switch is off turn it on
            if (state.OnOff == 0) {
            	state.OnOff = 1 // Update stored state to on
                sendEvent(name: "switch", value: "on")
            }
            
            // Send event to set the level (needed?)
            sendEvent(name: "level", value: state.level)
        }
    }
}

def configure() {
	log.debug "Configure called"
	["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 8 {${device.zigbeeId}} {}"]
}
