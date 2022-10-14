### MeleeNEAT stream bot system
A twitch bot that allows user to 'capture' AI models they see on stream. Captured models will be tied to their account in a remote database. These models are then available to be deployed in a number of potential future ways. Models that are captured will trigger a twitch clip, and cost some amount of channel points. 

The first use-case for the models captured will be a user-contributed catalogue of 'valuable' models produced by the divergent search system.
The second use-case will be for models to be programatically entered into a tournament (once a week/month). Viewers are incentivized to 'capture' the most interesting, or capable models in order to win the tournament and earn more channel points/accolades.
The third use-case will be for users to be able to 'download' their captured models and play with them locally via a distributed python client script.

- [ ] Kotlin project
  - [ ] Construct new Rewards for models that have been displayed on stream
    - [ ] Only construct a reward if its that model ID has never been acquired
    - [ ] Models should be created after the model finishes its turn on stream?
    - [ ] Models should have a cost that is in association with its server score?
    - [ ] Users can name model with the accompanying message of the reward redemption
  - [ ] Custom names for redeeming models
    - [ ] Profanity filter
  - [ ] Ability to impact evolution trajectory?
    - [ ] submit stored models into the set? (maybe with MCC)
  - [ ] Fix urls for dockerized runtime
- [ ] Svelte Dashboard project
- [ ] Integration between Kotlin Project and smash server
- [ ] Database layer
  - [ ] Mini-ktor http layer
    - [ ] api to write and read models
    - [ ] api to write twitch associations
    - [ ] api to support leaderboard for model performances (when deployed by user)
    - [ ] api to detail behavior metrics?
      - [ ] Appearence #
        - [ ] Score
        - [ ] Damage
        - [ ] Kills?
  - [ ] Document Database (Mongo)
  - [ ] Deploy locally in docker
- [ ] Cluster support for building models (distribute work)
- [ ] Consolidate model code into a separate project and include in all projects
  - [ ] reduce the copy/pasted domain code
- [x] Implement python NEAT network execution
- [x] Implement python hyperNEAT network construction
- [x] Resolve imbalance in query coordinates
The query coordinates for the hyper space lean toward the "left" or negative. Due to exclusive ranges and using the width or height as the denominator. So we'll get for a width of 10, values from [-1, .8]



## Questions

Where do we store models?
What information do we present on the CustomReward?
How do viewers/users use their Models?
What causes a model to be offered as a CustomReward?


10/13
- [x] Centralize the evaluator configuration on server and distribute to python client
- Create network visualizer for new dashboard
- Rework python to contribute stream stats to neatServer instead of old sveltekit dash
- Collect stats about training
  - Scores for current generation
    - Maybe have for multiple generations?
  - Species
  - Best models should be a list that is queryable
  - time to complete a generation
- Collect stats for stream runtime
  - Basic overtime stats
    - Wins, Losses, kills, deaths per evaluation
  - Ability to query for the network of the onstream model
    - Current model id on stream