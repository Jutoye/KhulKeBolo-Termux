package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class CommunityRule(
    val icon: ImageVector,
    val text: String
)

object AppGuidelines {
    val communityRules: List<CommunityRule> = listOf(
        CommunityRule(
            icon = Icons.Outlined.Hearing,
            text = "Yahan sirf ek dusre ko sunna hai, judge karna nahi."
        ),
        CommunityRule(
            icon = Icons.Outlined.Block,
            text = "Mazaak, gaali, dhamki, ya kisi ki taleem ka mazaak mana hai."
        ),
        CommunityRule(
            icon = Icons.Outlined.FavoriteBorder,
            text = "Flirt ya mulaqat ki baat mana hai."
        ),
        CommunityRule(
            icon = Icons.Outlined.Lock,
            text = "Apna ya kisi aur ka phone number, WhatsApp, Instagram, email ya link share karna mana hai."
        ),
        CommunityRule(
            icon = Icons.Outlined.RecordVoiceOver,
            text = "Listener advice tab hi de jab speaker khud maange."
        ),
        CommunityRule(
            icon = Icons.Outlined.DeleteSweep,
            text = "Koi bhi baat record nahi hoti. Session khatam hone par messages delete ho jaate hain."
        ),
        CommunityRule(
            icon = Icons.Outlined.Flag,
            text = "Kisi bhi waqt \"Khatam Karein\" ya \"Report\" kar sakte hain."
        )
    )

    val listenerSuggestedPhrases: List<String> = listOf(
        "Main sun raha hoon.",
        "Yeh sun kar bohat mushkil laga hoga.",
        "Aap ki baat samajh aa rahi hai.",
        "Aap jo kehna chahte hain, aap keh sakte hain.",
        "Aap ne yeh sab bataya, shukriya."
    )
}

@Composable
fun CommunityRulesCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AppGuidelines.communityRules.forEach { rule ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = rule.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = rule.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
