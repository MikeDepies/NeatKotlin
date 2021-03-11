import type { Character, Stage } from './types/Melee';
export const characters: Character[] = [
    'Bowser', 'DK', 'Doc', 'Falco', 'Falcon', 'Fox', 'Gannondorf', 'Ice Climbers', 'Jigglypuff', 'Kirby', 'Link', 'Luigi', 'Mario', 'Marth', 'Mewtwo', 'Mr. Game & Watch', 'Ness', 'Peach', 'Pichu', 'Pikachu', 'Roy', 'Samus', 'Yoshi', 'Young Link', 'Zelda'
]
export const stages: Stage[] = [
    "Battlefield",
    "Dreamland",
    "Final Destination",
    "Fountain Of Dreams",
    "Pokemon Stadium",
    "Yoshi Story",
    "Random Stage",
];
export function spriteY(character: Character) {
    const row1: Character[] = [
        "Doc",
        "Mario",
        "Luigi",
        "Bowser",
        "Peach",
        "Yoshi",
        "DK",
        "Falcon",
        "Gannondorf",
    ];
    const row2: Character[] = [
        "Falco",
        "Fox",
        "Ness",
        "Ice Climbers",
        "Kirby",
        "Samus",
        "Zelda",
        "Link",
        "Young Link",
    ];
    const row3: Character[] = [
        "Pichu",
        "Pikachu",
        "Jigglypuff",
        "Mewtwo",
        "Mr. Game & Watch",
        "Marth",
        "Roy",
    ];
    if (row1.includes(character)) {
        return -1;
    } else if (row2.includes(character)) {
        return -63;
    } else {
        return -124;
    }
}

export function spriteX(character: Character) {
    const column1: Character[] = [
        "Doc",
        "Falco",
    ];
    const column2: Character[] = [
        "Mario",
        "Fox",
        "Pichu"
    ];
    const column3: Character[] = [
        "Luigi",
        "Ness",
        "Pikachu"
    ];
    const column4: Character[] = [
        "Bowser",
        "Ice Climbers",
        "Jigglypuff"
    ];
    const column5: Character[] = [
        "Peach",
        "Kirby",
        "Mewtwo"
    ];
    const column6: Character[] = [
        "Yoshi",
        "Samus",
        "Mr. Game & Watch"
    ];
    const column7: Character[] = [
        "DK",
        "Zelda",
        "Marth"
    ];
    const column8: Character[] = [
        "Falcon",
        "Link",
        "Roy"
    ];
    const column9: Character[] = [
        "Gannondorf",
        "Young Link",
    ];
    if (column1.includes(character)) {
        return -1;
    } else if (column2.includes(character)) {
        return -70;
    } else if (column3.includes(character)) {
        return -138;
    } else if (column4.includes(character)) {
        return -207;
    } else if (column5.includes(character)) {
        return -276;
    } else if (column6.includes(character)) {
        return -345;
    } else if (column7.includes(character)) {
        return -414;
    } else if (column8.includes(character)) {
        return -483;
    } else {
        return -552;
    }
}