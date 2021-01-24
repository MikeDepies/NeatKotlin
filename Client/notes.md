- Added 4 new inputs per player character
    - onGround : Boolean
    - offStage : Boolean
    - invulnerable : Boolean
    - hitLag : Boolean

Note: This brings the total inputs up to 59. 
- Fixed an issue that would cause either damageDone or damageTaken to be set to a negative number upon stock take/stock loss.
    - This undoubtedly caused stunting of performance. Acting as a natural gaurd against our desired goal of taking stocks D:
- Combo Sequence (1 2 2 3 3 3 ...) multiplier is reset when the opponent touches ground - effectively ending the 'combo'.

Note: We wil probably play with this boundary more in the future.
- Added some new coloring to the charts to now reflect the current agents species. 
- SDs are now always 0 as oppose to score=sqrt(score)
- Fixed inacuraccies with dashboard score chart.
