package net.reminitous.mineciv.civ;

public enum CivilizationType {
    WARRIOR,
    ENGINEER,
    FARMER,
    MYSTIC,
    MERCHANT;

    public static CivilizationType fromButtonId(int id) {
        return switch (id) {
            case 0 -> WARRIOR;
            case 1 -> ENGINEER;
            case 2 -> FARMER;
            case 3 -> MYSTIC;
            case 4 -> MERCHANT;
            default -> FARMER;
        };
    }
}
