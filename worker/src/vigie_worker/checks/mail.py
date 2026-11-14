"""Analysis of the DNS records that protect a domain against email spoofing."""

from vigie_worker.checks.model import Check, Severity, failed, passed

CATEGORY = "courriel"


def check_spf(txt_records: list[str]) -> Check:
    title = "SPF"
    records = [r.strip() for r in txt_records if r.strip().lower().startswith("v=spf1")]
    if not records:
        return failed(
            "mail.spf",
            CATEGORY,
            title,
            Severity.MEDIUM,
            "Aucun enregistrement SPF : n'importe quel serveur peut envoyer des courriels "
            "au nom de ce domaine.",
            'Publier un enregistrement TXT, par exemple : "v=spf1 include:_spf.google.com -all" '
            '(ou "v=spf1 -all" si le domaine n\'envoie aucun courriel).',
        )
    if len(records) > 1:
        return failed(
            "mail.spf",
            CATEGORY,
            title,
            Severity.MEDIUM,
            f"{len(records)} enregistrements SPF trouvés : la norme n'en autorise qu'un, "
            "les serveurs de réception les ignorent tous.",
            "Fusionner les enregistrements SPF en un seul.",
        )
    terms = records[0].lower().split()
    if "+all" in terms or "all" in terms:
        return failed(
            "mail.spf",
            CATEGORY,
            title,
            Severity.HIGH,
            "Le SPF se termine par +all : il autorise explicitement tous les serveurs.",
            "Remplacer +all par -all.",
        )
    if "?all" in terms or not any(t.endswith("all") or t.startswith("redirect=") for t in terms):
        return failed(
            "mail.spf",
            CATEGORY,
            title,
            Severity.MEDIUM,
            "Le SPF ne dit pas quoi faire des serveurs non autorisés (?all ou aucune règle all).",
            "Terminer l'enregistrement par -all (ou ~all pendant la mise en place).",
        )
    return passed("mail.spf", CATEGORY, title, f"SPF publié : {records[0][:200]}")


def check_dmarc(txt_records: list[str]) -> Check:
    title = "DMARC"
    records = [r.strip() for r in txt_records if r.strip().lower().startswith("v=dmarc1")]
    if not records:
        return failed(
            "mail.dmarc",
            CATEGORY,
            title,
            Severity.MEDIUM,
            "Aucun enregistrement DMARC : les courriels usurpant ce domaine ne sont ni "
            "rejetés ni signalés.",
            'Publier un TXT sur _dmarc.<domaine>, par exemple : "v=DMARC1; p=quarantine; '
            'rua=mailto:dmarc@<domaine>"',
        )
    tags = {}
    for part in records[0].split(";"):
        if "=" in part:
            key, value = part.split("=", 1)
            tags[key.strip().lower()] = value.strip().lower()
    policy = tags.get("p")
    if policy in ("quarantine", "reject"):
        return passed("mail.dmarc", CATEGORY, title, f"DMARC actif avec la politique p={policy}.")
    if policy == "none":
        return failed(
            "mail.dmarc",
            CATEGORY,
            title,
            Severity.LOW,
            "DMARC est publié avec p=none : les courriels usurpés sont signalés mais "
            "toujours livrés.",
            "Passer à p=quarantine, puis p=reject une fois les rapports vérifiés.",
        )
    return failed(
        "mail.dmarc",
        CATEGORY,
        title,
        Severity.MEDIUM,
        "L'enregistrement DMARC n'a pas de politique valide (balise p manquante ou inconnue).",
        "Définir p=quarantine ou p=reject.",
    )
